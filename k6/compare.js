import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

// High-load comparison of the bottleneck endpoints.
//
// Pick ONE endpoint per run with the TARGET env var, then compare the
// http_req_duration p95/p99 between a "slow" run and its "fast" twin.
//
//   k6 run k6/compare.js                            # default: slow-sales
//   k6 run -e TARGET=fast-sales     k6/compare.js
//   k6 run -e TARGET=slow-authors   k6/compare.js
//   k6 run -e TARGET=fast-authors   k6/compare.js
//   k6 run -e TARGET=slow-upstream  k6/compare.js
//   k6 run -e TARGET=fast-upstream  k6/compare.js
//
// Tune load:   -e PEAK_VUS=50
// Point elsewhere: -e BASE_URL=http://localhost:8080
//
// Stream results to Grafana Cloud k6 (see README):
//   k6 cloud run k6/compare.js          # run FROM Grafana's cloud
//   k6 run --out cloud k6/compare.js    # run locally, results to Grafana Cloud

const ENDPOINTS = {
  'slow-authors': '/api/slow/authors',          // N+1: many queries
  'fast-authors': '/api/fast/authors',          // JOIN FETCH: 1 query
  'slow-sales': '/api/slow-query/sales-ranking', // correlated subquery: 1 expensive query
  'fast-sales': '/api/fast-query/sales-ranking', // window function: 1 cheap query
};

const TARGET = __ENV.TARGET || 'slow-sales';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PEAK_VUS = Number(__ENV.PEAK_VUS || 30);

const PATH = ENDPOINTS[TARGET];
if (!PATH) {
  throw new Error(`Unknown TARGET "${TARGET}". Use one of: ${Object.keys(ENDPOINTS).join(', ')}`);
}

// Per-request SQL statement count, read from the X-Sql-Statements header.
const sqlStatements = new Trend('sql_statements_per_request');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: PEAK_VUS }, // ramp up
        { duration: '30s', target: PEAK_VUS }, // hold at peak
        { duration: '10s', target: 0 },        // ramp down
      ],
      gracefulStop: '10s',
    },
  },
  thresholds: {
    // Keep this lenient: under high load the SLOW endpoints SHOULD breach it.
    // That red threshold is the point of the demo, not a script bug.
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<500'],
  },
  // Tag every metric with the endpoint so dashboards/Grafana Cloud k6 group cleanly.
  tags: { target: TARGET, path: PATH },
};

export default function () {
  const res = http.get(`${BASE_URL}${PATH}`, { tags: { target: TARGET } });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  // NOTE: X-Sql-Statements is a global counter, so under concurrency this
  // per-request number is noisy. Trust http_req_duration (p95/p99) as the real
  // signal under load; use single-request curl for the clean statement count.
  const count = res.headers['X-Sql-Statements'];
  if (count !== undefined) {
    sqlStatements.add(Number(count));
  }
}
