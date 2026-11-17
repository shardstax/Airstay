// k6 load test for the public browse path: GET /hotels/search (and /hotels/{id}/info for a share of users).
// Run with Docker, see load-tests/README.md. The measured numbers are written to results/<LABEL>.json and printed.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://app:8080/api/v1';
const CITIES = parseInt(__ENV.CITIES || '10');       // SeedCity-1 .. SeedCity-N (see seed.sql)
const EXECUTOR = __ENV.EXECUTOR || 'rate';           // 'rate' = open model (fixed req/s), 'vus' = closed model
const RATE = parseInt(__ENV.RATE || '100');          // iterations per second in 'rate' mode
const VUS = parseInt(__ENV.VUS || '50');             // virtual users in 'vus' mode
const DURATION = __ENV.DURATION || '60s';
const WARMUP = __ENV.WARMUP || '30s';
const LABEL = __ENV.LABEL || 'run';

const measured = EXECUTOR === 'vus'
  ? { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: WARMUP, exec: 'traffic' }
  : { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION, startTime: WARMUP,
      preAllocatedVUs: 100, maxVUs: 600, exec: 'traffic' };

export const options = {
  scenarios: {
    // Same traffic at a low rate first: JIT, connection pool and caches warm up and are NOT part of the results.
    warmup: { executor: 'constant-arrival-rate', rate: parseInt(__ENV.WARMUP_RATE || '100'), timeUnit: '1s',
              duration: WARMUP, preAllocatedVUs: 50, maxVUs: 200, exec: 'traffic' },
    measured,
  },
  // Thresholds that always pass exist only to make k6 keep these per-endpoint sub-metrics in the summary.
  thresholds: {
    'http_req_failed{scenario:measured}': ['rate<0.01'],
    'http_req_duration{scenario:measured,endpoint:search}': ['p(95)>=0'],
    'http_req_duration{scenario:measured,endpoint:info}': ['p(95)>=0'],
    'http_reqs{scenario:measured,endpoint:search}': ['count>=0'],
    'http_reqs{scenario:measured,endpoint:info}': ['count>=0'],
    'dropped_iterations{scenario:measured}': ['count>=0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// Skewed toward small numbers: a few cities and nearby dates are searched far more than the rest, like real traffic.
function skewed(max) {
  return Math.floor(max * Math.pow(Math.random(), 2));
}

function day(offset) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + offset);
  return d.toISOString().slice(0, 10);
}

export function traffic() {
  const city = `SeedCity-${1 + skewed(CITIES)}`;
  const startOffset = 1 + skewed(30);   // most people search the next few weeks
  const nights = 1 + skewed(3);
  const start = day(startOffset);
  const end = day(startOffset + nights);

  const res = http.get(
    `${BASE}/hotels/search?city=${city}&startDate=${start}&endDate=${end}&roomsCount=1&page=0&size=10`,
    { tags: { endpoint: 'search' } });
  const ok = check(res, { 'search returned 200': (r) => r.status === 200 });

  // A quarter of searchers open one of the hotels they found.
  if (ok && Math.random() < 0.25) {
    const hotels = res.json('data.content');
    if (hotels && hotels.length > 0) {
      const hotel = hotels[Math.floor(Math.random() * hotels.length)];
      const info = http.get(
        `${BASE}/hotels/${hotel.id}/info?startDate=${start}&endDate=${end}&roomsCount=1`,
        { tags: { endpoint: 'info' } });
      check(info, { 'info returned 200': (r) => r.status === 200 });
    }
  }
}

function find(data, metric, ...tags) {
  const key = Object.keys(data.metrics).find((k) => k.startsWith(`${metric}{`) && tags.every((t) => k.includes(t)));
  return key ? data.metrics[key].values : null;
}

const ms = (v) => (v === undefined || v === null ? '-' : v.toFixed(1));

export function handleSummary(data) {
  const lines = [`LABEL=${LABEL} mode=${EXECUTOR} ` +
    (EXECUTOR === 'vus' ? `vus=${VUS}` : `rate=${RATE}/s`) + ` duration=${DURATION} (after ${WARMUP} warm-up)`];
  lines.push('endpoint | requests | req/s | p50 ms | p90 ms | p95 ms | p99 ms | max ms');
  for (const endpoint of ['search', 'info']) {
    const d = find(data, 'http_req_duration', 'scenario:measured', `endpoint:${endpoint}`);
    if (!d) continue;
    const count = data.metrics[Object.keys(data.metrics).find((k) =>
      k.startsWith('http_reqs{') && k.includes('scenario:measured') && k.includes(`endpoint:${endpoint}`)) || '']?.values;
    // count.rate would divide by the whole test (warm-up included), so divide by the measured period instead.
    lines.push(`${endpoint} | ${count ? count.count : '-'} | ${count ? (count.count / parseInt(DURATION)).toFixed(1) : '-'} | ` +
      `${ms(d.med)} | ${ms(d['p(90)'])} | ${ms(d['p(95)'])} | ${ms(d['p(99)'])} | ${ms(d.max)}`);
  }
  const failed = find(data, 'http_req_failed', 'scenario:measured');
  // Only the measured period: drops during the warm-up of a freshly started JVM are not a result.
  const dropped = find(data, 'dropped_iterations', 'scenario:measured');
  lines.push(`failed requests: ${failed ? (failed.rate * 100).toFixed(2) + '%' : '-'}` +
    `   dropped iterations (system could not keep up): ${dropped ? dropped.count : 0}`);
  return {
    stdout: '\n' + lines.join('\n') + '\n',
    [`/scripts/results/${LABEL}.json`]: JSON.stringify(data, null, 2),
  };
}
