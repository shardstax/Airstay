# Search baseline (before any caching)

Measured 2026-11-17 with `load-tests/search.js` on the seeded data set from `load-tests/seed.sql`. Each run: 30 s warm-up
(excluded), then 60 s measured. Nothing in the application was cached at this point.

## Environment (everything shares one machine, including the load generator)
- Windows 11 laptop; Docker Desktop (engine 29.4.2) with 8 CPUs and 9.6 GB available to Docker.
- App: Spring Boot 3.5.7, Java 21 (Alpine JRE container, default JVM flags, no CPU limit). PostgreSQL 16 container, default settings.
- Data: 300 hotels, 1,200 room types, **439,200 inventory rows**, 109,800 nightly "from" prices (inventory table 105 MB).
- Traffic: each iteration is one `GET /hotels/search` (city, start date and stay length skewed toward popular values);
  25% of iterations also call `GET /hotels/{id}/info` for a hotel found. Open model = fixed arrival rate.
- The k6 load generator runs on the same CPUs as the app and the database, so the saturation point is lower than it
  would be with a separate load generator.

## Results

| Mode | Search req/s | Search p50 | p95 | p99 | max | Failed | Dropped |
|---|---|---|---|---|---|---|---|
| Open, 100 iterations/s | 100.0 | 3.6 ms | **5.9 ms** | 7.5 ms | 29 ms | 0% | 0 |
| Open, 300 iterations/s | 300.0 | 4.1 ms | **14.1 ms** | 34.4 ms | 130 ms | 0% | 0 |
| Open, 600 iterations/s | 595.5 | 5.9 ms | **82.0 ms** | 270 ms | 1,532 ms | 0% | 268 |
| Closed, 100 concurrent users | 944.9 | 76.5 ms | **215.6 ms** | 303 ms | 716 ms | 0% | n/a |

`GET /hotels/{id}/info` (about 25% as many requests): p95 3.8 ms, 9.0 ms, 70.3 ms and 208.4 ms in the same four runs.

## What this says
- At realistic load the search endpoint is already fast: **p95 about 6 ms at 100 searches/s** (360,000 searches/hour).
- The knee is between 300 and 600 searches/s. At 600/s p95 grows to 82 ms and 268 requests (0.7%) were dropped by the
  load generator because the system could not keep up.
- Maximum throughput with 100 concurrent users is about **945 searches/s** (plus about 238 info requests/s), with
  latency dominated by queueing (p95 216 ms).
- The database is not the slow part: the equivalent SQL runs in about **1.3 ms** in PostgreSQL (index scans on
  `idx_inventory_city_date` and `unique_hotel_min_price_date`, checked with EXPLAIN ANALYZE). The rest is application
  work (Hibernate hydration, the count query, JSON serialisation, security filter).

## Consequence for the cache decision
A latency argument for caching search is weak: p95 is already single-digit milliseconds at 100 searches/s. The only
argument is **capacity**: how many searches per second the same hardware sustains before latency degrades. Any cache
result will be compared against the rows above, with the same script, data and machine.
