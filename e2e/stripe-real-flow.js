// Real Stripe SANDBOX end-to-end check of the payment flow. Not part of `mvn verify` (it needs Stripe keys and the stack).
// See e2e/README.md. It uses Stripe test cards only (no money can move) and prints a PASS/FAIL line per step.
const { chromium } = require('playwright');

const API = process.env.API_BASE || 'http://host.docker.internal:8080/api/v1';
const KEY = process.env.STRIPE_SECRET_KEY || '';
if (!KEY.startsWith('sk_test_')) {
  console.error('STRIPE_SECRET_KEY must be a Stripe TEST-mode key (sk_test_...). Refusing to run.');
  process.exit(2);
}

let failures = 0;
const step = (ok, text) => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${text}`); if (!ok) failures++; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const day = (n) => { const d = new Date(); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };

async function api(method, path, token, body) {
  const r = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON */ }
  return { status: r.status, json, text };
}

const stripe = async (method, path) => (await fetch('https://api.stripe.com/v1' + path, {
  method,
  headers: { Authorization: 'Basic ' + Buffer.from(KEY + ':').toString('base64') },
})).json();

async function signup(email, name, accountType) {
  let r = await api('POST', '/auth/signup', null, { email, password: 'Password123!', name, accountType });
  if (r.status !== 201) throw new Error(`signup failed: ${r.status} ${r.text}`);
  r = await api('POST', '/auth/login', null, { email, password: 'Password123!' });
  return r.json.data.accessToken;
}

async function waitStatus(bookingId, token, wanted, seconds = 60) {
  let status = '';
  for (let i = 0; i < seconds / 1.5; i++) {
    status = (await api('GET', `/bookings/${bookingId}/status`, token)).json?.data?.bookingStatus;
    if (status === wanted) break;
    await sleep(1500);
  }
  return status;
}

// Stripe's hosted checkout keeps its fields in iframes that selectors cannot reach, so this clicks and types at fixed
// positions in a 1280x1300 viewport. If Stripe changes the page layout, this is the part to update.
async function payOnCheckout(page, url, card) {
  await page.setViewportSize({ width: 1280, height: 1300 });
  await page.goto(url, { waitUntil: 'load' });
  await sleep(6000);
  const type = async (x, y, t) => { await page.mouse.click(x, y); await page.keyboard.type(t, { delay: 40 }); };
  await type(850, 385, card);
  await type(800, 424, '1234');
  await type(990, 424, '123');
  await type(900, 584, 'E2E Traveller');
  await type(900, 660, '1 Test Street');
  await sleep(1200);
  await page.mouse.click(785, 757);            // "Enter address manually"
  await sleep(1500);
  await type(900, 739, 'Pune');
  await type(900, 777, '411001');
  await page.mouse.click(900, 815);
  await sleep(800);
  await page.keyboard.type('Maharashtra');
  await page.keyboard.press('Enter');
  await sleep(1000);
  await page.mouse.click(903, 889);            // Pay
}

(async () => {
  const id = Date.now();
  const manager = await signup(`e2e-mgr-${id}@example.test`, 'E2E Manager', 'HOTEL_MANAGER');
  const user = await signup(`e2e-user-${id}@example.test`, 'E2E Guest');
  let r = await api('POST', '/admin/hotels', manager, { name: `E2E Stripe Hotel ${id}`, placeId: 1259229 });
  const hotelId = r.json.data.id;
  r = await api('POST', `/admin/hotels/${hotelId}/rooms`, manager, { type: 'Deluxe', basePrice: 2000, totalCount: 3, capacity: 2 });
  const roomId = r.json.data.id;
  await api('PATCH', `/admin/hotels/${hotelId}/activate`, manager);
  const guestId = (await api('POST', '/users/guests', user, { name: 'E2E Traveller' })).json.data.id;

  async function startPayment(offset) {
    let res = await api('POST', '/bookings/init', user, { hotelId, roomId, checkInDate: day(offset), checkOutDate: day(offset + 2), roomsCount: 1 });
    if (res.status !== 201) throw new Error(`init failed: ${res.status} ${res.text}`);
    const bookingId = res.json.data.id;
    await api('POST', `/bookings/${bookingId}/addGuests`, user, [guestId]);
    res = await api('POST', `/bookings/${bookingId}/payments`, user);
    if (res.status !== 200) {
      throw new Error(`payments failed: ${res.status} ${res.text} (is the app running with real Stripe keys, PAYMENTS_FAKE=false?)`);
    }
    return { bookingId, url: res.json.data.sessionUrl };
  }

  const browser = await chromium.launch();
  try {
    // 1. Pay with the test card; the real webhook arrives through the Stripe CLI container.
    const paid = await startPayment(20);
    step(new URL(paid.url).host === 'checkout.stripe.com', `checkout session is on checkout.stripe.com (booking ${paid.bookingId})`);
    const page = await browser.newPage();
    await payOnCheckout(page, paid.url, '4242424242424242');
    step(await waitStatus(paid.bookingId, user, 'CONFIRMED') === 'CONFIRMED', 'paid with 4242 test card: webhook arrived, booking CONFIRMED');

    // 2. Cancel: a real refund through the Kafka refund worker.
    step((await api('POST', `/bookings/${paid.bookingId}/cancel`, user)).status === 204, 'cancel answered 204');
    let refund;
    for (let i = 0; i < 30 && !refund; i++) {
      await sleep(2000);
      refund = ((await stripe('GET', '/refunds?limit=5')).data || []).find((x) => Date.now() / 1000 - x.created < 300);
    }
    step(!!refund && refund.status === 'succeeded',
      `Stripe refund succeeded (${refund ? refund.currency.toUpperCase() + ' ' + refund.amount / 100 : 'none found'})`);
    step(await waitStatus(paid.bookingId, user, 'CANCELLED', 15) === 'CANCELLED', 'booking CANCELLED');

    // 3. Declined card: the booking stays unpaid; expiring the session releases it.
    const declined = await startPayment(40);
    const page2 = await browser.newPage();
    await payOnCheckout(page2, declined.url, '4000000000000002');
    await sleep(9000);
    step((await api('GET', `/bookings/${declined.bookingId}/status`, user)).json.data.bookingStatus === 'PAYMENTS_PENDING',
      'declined card (4000...0002): booking still PAYMENTS_PENDING');
    const sessionId = declined.url.match(/cs_test_[A-Za-z0-9]+/)[0];
    step((await stripe('POST', `/checkout/sessions/${sessionId}/expire`)).status === 'expired', 'checkout session expired in Stripe');
    step(await waitStatus(declined.bookingId, user, 'EXPIRED', 30) === 'EXPIRED', 'expired webhook released the booking: EXPIRED');
  } finally {
    await browser.close();
  }

  console.log(failures === 0 ? '\nALL STEPS PASSED' : `\n${failures} STEP(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
})().catch((e) => { console.error('ERROR', e.message); process.exit(1); });
