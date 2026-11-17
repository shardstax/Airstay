// k6 load test of the WHOLE booking flow, in two modes. Run it through load-tests/run-booking-load.sh, which also
// checks the database afterwards for overbooking and prints resource use (see load-tests/README.md).
//
// The app must run with PAYMENTS_FAKE=true (no Stripe account needed; the webhook is still verified with its real signature
// check) and RATE_LIMIT_ENABLED=false (otherwise the rate limiter would answer 429 to the load, which is not what is measured).
//
// MODE=mixed (default): a realistic mix, open model (a fixed number of new "shoppers" per second, whatever the response times).
//   Each shopper: search -> open one hotel -> hold a room (POST /bookings/init) -> add guests -> start payment -> signed
//   Stripe webhook -> check CONFIRMED. A share abandons after the hold (the hold stays until it expires) and a share cancels
//   after confirming. Many shoppers want the same popular nights, so some holds are rejected as sold out (409), as in real life.
// MODE=contention: a closed model, many users all trying to book the SAME few rooms for the SAME nights at once. Stock is
//   limited, so exactly that many bookings may succeed and every other attempt must get a clean 409.
//
// Test data is created in setup() through the API: hotels named "Load Hotel <RUN_ID> <n>" (removed by unload.sql) and users.
import http from 'k6/http';
import crypto from 'k6/crypto';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const int = (name, fallback) => parseInt(__ENV[name] || String(fallback), 10);
const num = (name, fallback) => parseFloat(__ENV[name] || String(fallback));

const BASE = __ENV.BASE_URL || 'http://app:8080/api/v1';
const MODE = __ENV.MODE || 'mixed';
const RUN_ID = __ENV.RUN_ID || String(Date.now());
const LABEL = __ENV.LABEL || `booking-${MODE}-${RUN_ID}`;
const WEBHOOK_SECRET = __ENV.STRIPE_WEBHOOK_SECRET || 'whsec_not_configured';
const PASSWORD = 'Password123!';
const USERS = int('USERS', 60);
const DURATION = __ENV.DURATION || (MODE === 'contention' ? '45s' : '120s');
// mixed
const HOTELS = int('HOTELS', 20);
const CITIES = int('CITIES', 3);
const ROOMS = int('ROOMS', 3);          // room types per hotel
const STOCK = int('STOCK', 4);          // rooms of each type
const DAYS = int('DAYS', 30);           // shoppers pick a start date within the next DAYS days (skewed to the near ones)
const RATE = int('RATE', 10);           // new shoppers per second
const WARMUP = __ENV.WARMUP || '30s';
const WARMUP_RATE = int('WARMUP_RATE', 2);
const ABANDON = num('ABANDON', 0.10);   // share that leaves after the hold
const CANCEL = num('CANCEL', 0.05);     // share that cancels after confirming
// contention
const VUS = int('VUS', 100);
const STOCKS = (__ENV.STOCKS || '5,10,20').split(',').map((s) => parseInt(s, 10));

const MEASURED = MODE === 'contention' ? 'contention' : 'measured';

// Run totals (whole run, warm-up included); compare them by eye with the booking counts the runner prints from the database.
const flows = new Counter('flows_started');
const holds = new Counter('holds_created');          // POST /bookings/init answered 201
const soldOut = new Counter('holds_sold_out');       // 409 "not available": the clean answer when stock is gone
const busy = new Counter('holds_busy');              // 409 "resource busy": a lock conflict, NOT expected
const confirmed = new Counter('bookings_confirmed');
const cancelled = new Counter('bookings_cancelled');
const abandoned = new Counter('flows_abandoned');
const noResults = new Counter('flows_no_results');   // search or hotel page showed nothing to book
const serverErrors = new Counter('server_errors');   // any 5xx
const unexpected = new Counter('unexpected_answers');// any answer that is neither a success nor the clean sold-out 409

const ENDPOINTS = ['search', 'info', 'init', 'addGuests', 'payments', 'webhook', 'status', 'cancel'];
const COUNTERS = ['flows_started', 'holds_created', 'holds_sold_out', 'holds_busy', 'bookings_confirmed', 'bookings_cancelled',
  'flows_abandoned', 'flows_no_results', 'server_errors', 'unexpected_answers'];

const scenarios = MODE === 'contention'
  ? { contention: { executor: 'constant-vus', vus: VUS, duration: DURATION, exec: 'contention' } }
  : {
      // Low-rate traffic first so the JVM, the connection pool and the caches are warm; not part of the results.
      warmup: { executor: 'constant-arrival-rate', rate: WARMUP_RATE, timeUnit: '1s', duration: WARMUP,
                preAllocatedVUs: 20, maxVUs: 200, exec: 'shopper' },
      measured: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION, startTime: WARMUP,
                  preAllocatedVUs: 100, maxVUs: 1500, exec: 'shopper' },
    };

// Thresholds that always pass exist only to make k6 keep these sub-metrics (measured period only) in the summary.
const thresholds = { checks: ['rate>0.999'] };
for (const endpoint of ENDPOINTS) {
  thresholds[`http_req_duration{scenario:${MEASURED},endpoint:${endpoint}}`] = ['p(95)>=0'];
  thresholds[`http_reqs{scenario:${MEASURED},endpoint:${endpoint}}`] = ['count>=0'];
}
thresholds[`dropped_iterations{scenario:${MEASURED}}`] = ['count>=0'];
thresholds[`http_reqs{scenario:${MEASURED}}`] = ['count>=0'];

export const options = {
  setupTimeout: '600s',
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ---- helpers ------------------------------------------------------------------------------------------------

// Skewed toward small numbers: a few cities, hotels and nearby dates are far more popular than the rest.
const skewed = (max) => Math.floor(max * Math.pow(Math.random(), 2));
const pick = (list) => list[Math.floor(Math.random() * list.length)];

function day(offset) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + offset);
  return d.toISOString().slice(0, 10);
}

function call(method, path, token, body, endpoint, extraHeaders, expected) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, extraHeaders || {});
  if (token) headers.Authorization = `Bearer ${token}`;
  const params = { headers, tags: { endpoint } };
  if (expected) params.responseCallback = http.expectedStatuses(...expected);
  const payload = body === undefined || body === null ? null : (typeof body === 'string' ? body : JSON.stringify(body));
  const response = http.request(method, `${BASE}${path}`, payload, params);
  if (response.status >= 500) serverErrors.add(1);
  return response;
}

function expectStatus(response, step, ...allowed) {
  const ok = allowed.includes(response.status);
  check(response, { [`${step}: ${allowed.join(' or ')}`]: () => ok });
  if (!ok) unexpected.add(1);
  return ok;
}

function signUpAndLogin(email, name, accountType) {
  let response = http.post(`${BASE}/auth/signup`, JSON.stringify({ email, password: PASSWORD, name, accountType }),
    { headers: { 'Content-Type': 'application/json' } });
  if (response.status === 429) fail('Rate limiting answered 429: start the app with RATE_LIMIT_ENABLED=false (see load-tests/README.md)');
  if (response.status !== 201) fail(`sign-up failed: HTTP ${response.status} ${response.body}`);
  response = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  if (response.status !== 200) fail(`login failed: HTTP ${response.status} ${response.body}`);
  return response.json('data.accessToken');
}

// Room types must come from the catalog (GET /catalog).
const ROOM_TYPES = ['Standard', 'Deluxe', 'Superior', 'Suite', 'Family', 'Studio', 'Villa', 'Dormitory'];

// placeId: any number, because the app runs with PLACES_PROVIDER=synthetic for load tests (see README section 6).
function createHotel(managerToken, index, placeId, stocks) {
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${managerToken}` };
  let response = http.post(`${BASE}/admin/hotels`, JSON.stringify({ name: `Load Hotel ${RUN_ID} ${index}`, placeId }), { headers });
  if (response.status !== 201) fail(`create hotel failed: HTTP ${response.status} ${response.body}`);
  const hotelId = response.json('data.id');
  const city = response.json('data.city'); // the place label the server derived: what search matches on
  const rooms = [];
  stocks.forEach((stock, roomIndex) => {
    response = http.post(`${BASE}/admin/hotels/${hotelId}/rooms`, JSON.stringify({
      type: ROOM_TYPES[roomIndex % ROOM_TYPES.length], basePrice: 1500 + roomIndex * 500, totalCount: stock, capacity: 2 }), { headers });
    if (response.status !== 201) fail(`create room failed: HTTP ${response.status} ${response.body}`);
    rooms.push({ id: response.json('data.id'), stock });
  });
  response = http.patch(`${BASE}/admin/hotels/${hotelId}/activate`, null, { headers });
  if (response.status !== 204) fail(`activate hotel failed: HTTP ${response.status} ${response.body}`);
  return { id: hotelId, city, rooms };
}

// ---- setup: hotels and users through the API ----------------------------------------------------------------

export function setup() {
  const managerToken = signUpAndLogin(`load-manager-${RUN_ID}@example.test`, 'Load Manager', 'HOTEL_MANAGER');

  const hotels = [];
  if (MODE === 'contention') {
    hotels.push(createHotel(managerToken, 0, 3_000_000 + (Number(RUN_ID) % 1_000_000) * 100, STOCKS));
  } else {
    for (let i = 0; i < HOTELS; i++) {
      hotels.push(createHotel(managerToken, i, 3_000_000 + (Number(RUN_ID) % 1_000_000) * 100 + (i % CITIES), Array(ROOMS).fill(STOCK)));
    }
  }

  const users = [];
  for (let u = 0; u < USERS; u++) {
    const token = signUpAndLogin(`load-user-${RUN_ID}-${u}@example.test`, `Load User ${u}`, undefined);
    const response = http.post(`${BASE}/users/guests`, JSON.stringify({ name: `Traveller ${u}` }),
      { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });
    if (response.status !== 201) fail(`create guest failed: HTTP ${response.status} ${response.body}`);
    users.push({ token, guestId: response.json('data.id') });
  }
  const cities = [...new Set(hotels.map((hotel) => hotel.city))];
  console.log(`setup done: ${hotels.length} hotels, ${hotels.reduce((n, h) => n + h.rooms.length, 0)} room types, ${users.length} users`);
  return { hotels, users, cities };
}

// ---- the steps of a booking ------------------------------------------------------------------------------------

function hold(user, hotelId, roomId, checkIn, checkOut) {
  const key = `load-${RUN_ID}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2)}`;
  const response = call('POST', '/bookings/init', user.token,
    { hotelId, roomId, checkInDate: checkIn, checkOutDate: checkOut, roomsCount: 1 }, 'init',
    { 'Idempotency-Key': key }, [201, 409]);
  if (response.status === 201) {
    holds.add(1);
    return response.json('data.id');
  }
  if (response.status === 409) {
    const message = (response.json('error.message') || '').toString();
    if (message.includes('busy')) {
      busy.add(1);
      check(response, { 'init: no lock conflict ("resource busy")': () => false });
    } else if (message.includes('not available')) {
      soldOut.add(1);
    } else {
      // Some other conflict (for example an idempotency clash): never count it as a clean "sold out".
      unexpected.add(1);
      check(response, { 'init: 409 is "not available" or "busy"': () => false });
    }
    return null;
  }
  unexpected.add(1);
  check(response, { 'init: 201 or 409': () => false });
  return null;
}

// Add guests, start payment, send the signed webhook and check the booking is CONFIRMED. Returns true when confirmed.
function payAndConfirm(user, bookingId) {
  let response = call('POST', `/bookings/${bookingId}/addGuests`, user.token, [user.guestId], 'addGuests');
  if (!expectStatus(response, 'addGuests', 200)) return false;

  response = call('POST', `/bookings/${bookingId}/payments`, user.token, null, 'payments');
  if (!expectStatus(response, 'payments', 200)) return false;
  const sessionId = (response.json('data.sessionUrl') || '').toString().split('/').pop();

  const payload = JSON.stringify({
    id: `evt_load_${sessionId}`, object: 'event', api_version: '2025-03-31.basil',
    created: Math.floor(Date.now() / 1000), livemode: false, type: 'checkout.session.completed',
    data: { object: { id: sessionId, object: 'checkout.session', payment_status: 'paid', status: 'complete' } },
  });
  const timestamp = Math.floor(Date.now() / 1000);
  const signature = crypto.hmac('sha256', WEBHOOK_SECRET, `${timestamp}.${payload}`, 'hex');
  response = call('POST', '/webhook/payment', null, payload, 'webhook', { 'Stripe-Signature': `t=${timestamp},v1=${signature}` });
  if (!expectStatus(response, 'webhook', 204)) return false;

  response = call('GET', `/bookings/${bookingId}/status`, user.token, null, 'status');
  const isConfirmed = expectStatus(response, 'status', 200) && response.json('data.bookingStatus') === 'CONFIRMED';
  check(response, { 'status: CONFIRMED after the webhook': () => isConfirmed });
  if (isConfirmed) confirmed.add(1);
  return isConfirmed;
}

function maybeCancel(user, bookingId) {
  if (Math.random() >= CANCEL) return;
  const response = call('POST', `/bookings/${bookingId}/cancel`, user.token, null, 'cancel');
  if (expectStatus(response, 'cancel', 204)) cancelled.add(1);
}

// ---- mode: mixed -------------------------------------------------------------------------------------------------

export function shopper(data) {
  flows.add(1);
  const user = pick(data.users);
  const city = data.cities[skewed(data.cities.length)];
  const startOffset = 1 + skewed(DAYS);
  const nights = 1 + skewed(3);
  const checkIn = day(startOffset);
  const checkOut = day(startOffset + nights);

  let response = call('GET', `/hotels/search?city=${city}&startDate=${checkIn}&endDate=${checkOut}&roomsCount=1&page=0&size=10`,
    null, null, 'search');
  if (!expectStatus(response, 'search', 200)) return;
  const found = response.json('data.content') || [];
  if (found.length === 0) { noResults.add(1); return; }

  const hotelId = found[skewed(found.length)].id;
  response = call('GET', `/hotels/${hotelId}/info?startDate=${checkIn}&endDate=${checkOut}&roomsCount=1`, null, null, 'info');
  if (!expectStatus(response, 'info', 200)) return;
  const available = (response.json('data.rooms') || []).filter((room) => room.price !== null && room.price !== undefined);
  if (available.length === 0) { noResults.add(1); return; }

  const bookingId = hold(user, hotelId, pick(available).id, checkIn, checkOut);
  if (bookingId === null) return;
  if (Math.random() < ABANDON) { abandoned.add(1); return; }
  if (payAndConfirm(user, bookingId)) maybeCancel(user, bookingId);
}

// ---- mode: contention ----------------------------------------------------------------------------------------------

export function contention(data) {
  flows.add(1);
  const user = data.users[(__VU - 1) % data.users.length];
  const hotel = data.hotels[0];
  const room = pick(hotel.rooms);
  // Everyone wants the same nights.
  const bookingId = hold(user, hotel.id, room.id, day(30), day(32));
  if (bookingId !== null) payAndConfirm(user, bookingId);
}

// ---- results ----------------------------------------------------------------------------------------------------------

export function handleSummary(data) {
  const metric = (name) => (data.metrics[name] ? data.metrics[name].values : {});
  const total = (name) => metric(name).count || 0;
  const perEndpoint = {};
  for (const endpoint of ENDPOINTS) {
    const duration = metric(`http_req_duration{scenario:${MEASURED},endpoint:${endpoint}}`);
    const count = metric(`http_reqs{scenario:${MEASURED},endpoint:${endpoint}}`).count || 0;
    if (count > 0) {
      perEndpoint[endpoint] = { count, p50: duration.med, p90: duration['p(90)'], p95: duration['p(95)'], p99: duration['p(99)'], max: duration.max };
    }
  }
  const measuredRequests = metric(`http_reqs{scenario:${MEASURED}}`).count || 0;
  const measuredSeconds = seconds(DURATION);
  const compact = {
    label: LABEL, mode: MODE, runId: RUN_ID, rate: MODE === 'contention' ? null : RATE, vus: MODE === 'contention' ? VUS : null,
    duration: DURATION, requestsPerSecond: measuredRequests / measuredSeconds,
    droppedIterations: (metric(`dropped_iterations{scenario:${MEASURED}}`).count || 0),
    totals: Object.fromEntries(COUNTERS.map((name) => [name, total(name)])),
    checksPassRate: metric('checks').rate,
    perEndpoint,
  };

  const lines = [`\nBooking load, mode ${MODE}, run ${RUN_ID}`,
    `  whole run: ${compact.totals.flows_started} shoppers started, ${compact.totals.holds_created} holds created, ` +
    `${compact.totals.holds_sold_out} sold out (409), ${compact.totals.holds_busy} lock conflicts, ` +
    `${compact.totals.bookings_confirmed} confirmed, ${compact.totals.bookings_cancelled} cancelled, ` +
    `${compact.totals.flows_abandoned} abandoned, ${compact.totals.flows_no_results} found nothing to book`,
    `  errors: ${compact.totals.server_errors} server errors (5xx), ${compact.totals.unexpected_answers} unexpected answers, ` +
    `checks passed ${(compact.checksPassRate * 100).toFixed(2)}%, dropped iterations in the measured period ${compact.droppedIterations}`,
    `  measured period: ${compact.requestsPerSecond.toFixed(1)} requests/s`];
  for (const [endpoint, v] of Object.entries(perEndpoint)) {
    lines.push(`    ${endpoint.padEnd(10)} n=${String(v.count).padStart(6)}  p50 ${v.p50.toFixed(1)} ms  p95 ${v.p95.toFixed(1)} ms  p99 ${v.p99.toFixed(1)} ms  max ${v.max.toFixed(0)} ms`);
  }
  return {
    stdout: `${lines.join('\n')}\n`,
    [`/scripts/results/${LABEL}.json`]: JSON.stringify(data),
    [`/scripts/results/${LABEL}.summary.json`]: JSON.stringify(compact, null, 2),
  };
}

// "45s", "2m", "1h30m", "500ms" -> seconds.
function seconds(duration) {
  let total = 0;
  for (const [, amount, unit] of duration.matchAll(/(\d+(?:\.\d+)?)(ms|s|m|h)/g)) {
    total += parseFloat(amount) * { ms: 0.001, s: 1, m: 60, h: 3600 }[unit];
  }
  return total > 0 ? total : parseFloat(duration);
}
