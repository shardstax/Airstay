// k6 scenario: how long does it take to ACTIVATE a hotel?
//
// Each iteration creates a hotel with ROOMS room types and then activates it (PATCH /admin/hotels/{id}/activate). Activation
// creates one inventory row per room type per night for a year and prices them, so its cost grows with the number of room
// types. Only the activation request is timed (metric activation_ms); creating the hotel and rooms is not.
//
// Run (see load-tests/README.md for the whole procedure):
//   docker run --rm -i --network airstay_default -e BASE_URL=http://app:8080/api/v1 -e ROOMS=4 -e ITERATIONS=30 \
//     -e LABEL=activation-before -v "$PWD/load-tests:/scripts" grafana/k6:latest run /scripts/activation.js
//
// Hotels are named "Bench Hotel ..." so load-tests/unbench.sql can remove them afterwards.
import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://app:8080/api/v1';
const ROOMS = parseInt(__ENV.ROOMS || '4', 10);
const ITERATIONS = parseInt(__ENV.ITERATIONS || '30', 10);
const LABEL = __ENV.LABEL || 'activation';

const activation = new Trend('activation_ms', true);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  // One user, one hotel at a time: this measures the cost of one activation, not concurrency.
  scenarios: {
    activate: { executor: 'shared-iterations', vus: 1, iterations: ITERATIONS, maxDuration: '30m' },
  },
  thresholds: { checks: ['rate==1'] },
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'max'],
};

export function setup() {
  const email = `bench-${Date.now()}@example.test`;
  let response = http.post(`${BASE_URL}/auth/signup`, JSON.stringify({
    email, password: 'Password123!', name: 'Bench Manager', accountType: 'HOTEL_MANAGER',
  }), { headers: JSON_HEADERS });
  if (response.status !== 201) fail(`sign-up failed: HTTP ${response.status} ${response.body}`);
  response = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ email, password: 'Password123!' }), { headers: JSON_HEADERS });
  if (response.status !== 200) fail(`login failed: HTTP ${response.status} ${response.body}`);
  return { token: response.json('data.accessToken') };
}

export default function (data) {
  const headers = { ...JSON_HEADERS, Authorization: `Bearer ${data.token}` };
  const unique = `${__ITER}-${Date.now()}`;

  let response = http.post(`${BASE_URL}/admin/hotels`,
    JSON.stringify({ name: `Bench Hotel ${unique}`, city: `Bench-${unique}` }), { headers });
  check(response, { 'hotel created (201)': (r) => r.status === 201 });
  const hotelId = response.json('data.id');

  for (let room = 0; room < ROOMS; room++) {
    response = http.post(`${BASE_URL}/admin/hotels/${hotelId}/rooms`,
      JSON.stringify({ type: `Room type ${room}`, basePrice: 1500 + room * 500, totalCount: 5, capacity: 2 }), { headers });
    check(response, { 'room created (201)': (r) => r.status === 201 });
  }

  response = http.patch(`${BASE_URL}/admin/hotels/${hotelId}/activate`, null, { headers });
  check(response, { 'hotel activated (204)': (r) => r.status === 204 });
  activation.add(response.timings.duration);
}

export function handleSummary(data) {
  const values = data.metrics.activation_ms.values;
  const text = `\nActivation of a hotel with ${ROOMS} room types (${ITERATIONS} runs): ` +
    `median ${values.med.toFixed(0)} ms, p95 ${values['p(95)'].toFixed(0)} ms, ` +
    `min ${values.min.toFixed(0)} ms, max ${values.max.toFixed(0)} ms\n`;
  return { stdout: text, [`/scripts/results/${LABEL}.json`]: JSON.stringify(data) };
}
