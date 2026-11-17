# Search with the Redis cache, compared with the baseline

Measured 2026-11-17 with the same script, data (439,200 inventory rows), machine and settings as
[`baseline.md`](baseline.md). Only the cache differs: `SEARCH_CACHE_ENABLED` on (30 s TTL, plus or minus 20%) or off.
Each run: 30 s warm-up (excluded), then 60 s measured. Redis stats were reset before every cache run.

## Decision rule that was set beforehand
Keep the cache only if it raises capacity by about 1.5 times or more; otherwise remove it. **Result: kept** (2.24 times).

## Results (search endpoint)

| Mode | Cache | Search req/s | p50 | p95 | p99 | Dropped | Hit ratio |
|---|---|---|---|---|---|---|---|
| Open, 300 iterations/s | off | 300.0 | 4.1 ms | 14.1 ms | 34.4 ms | 0 | n/a |
| Open, 300 iterations/s | **on** | 300.0 | **1.5 ms** | **5.9 ms** | 11.6 ms | 0 | 88.9% |
| Open, 600 iterations/s | off | 595.5 | 5.9 ms | **82.0 ms** | 270 ms | 268 | n/a |
| Open, 600 iterations/s | **on** | 600.0 | **1.5 ms** | **10.4 ms** | 38.1 ms | 0 | 93.6% |
| Closed, 100 concurrent users | off | **944.9** | 76.5 ms | 215.6 ms | 303 ms | n/a | n/a |
| Closed, 100 concurrent users | **on** | **2,119.3** | **31.5 ms** | **79.8 ms** | 122.9 ms | n/a | 97.9% |

- **Capacity: 945 to 2,119 searches/s (2.24 times)** at 100 concurrent users, with p95 down from 216 ms to 80 ms.
- At 600 searches/s the uncached system was saturated (p95 82 ms, 268 requests dropped); with the cache it handled
  the same rate with p95 10 ms and no drops.
- Hit ratios (Redis `keyspace_hits` / `keyspace_misses`, warm-up included) grow with load because repeats within the
  30 s TTL become more likely: 78.6% at 100/s, 88.9% at 300/s, 93.6% at 600/s, 97.9% closed-loop. 540 to 865 distinct
  searches were cached during these runs.

## Low load (100 searches/s): the median improves, the tail is within noise

Warm JVMs, two runs each, 100 iterations/s:

| Cache | p50 | p95 | p99 |
|---|---|---|---|
| on | 2.1 ms, 2.1 ms | 7.7 ms, 8.1 ms | 11.1 ms, 13.8 ms |
| off | 4.5 ms, 4.3 ms | 10.0 ms, 8.3 ms | 17.5 ms, 13.3 ms |

The median halves consistently. The p95 difference is inside run-to-run noise (the original uncached run measured
5.9 ms, a later uncached run 10.0 ms), so **no low-load p95 improvement is claimed**. An early cache-on run right
after an app restart showed p95 11.2 ms, which was a cold JVM, and the `info` endpoint (not cached) doubled in that same
run.

## Failure behaviour (checked live)
With the Redis container stopped, all searches still returned 200. Only the first two were slower (440 ms and 240 ms:
the 200 ms Redis timeouts), then the circuit breaker opened and searches were fast again (about 25 ms) while Redis was
still down. After Redis returned and the 10 s cool-down passed, caching resumed. The log held one warning, not a flood.

## Caveats (read before quoting any number)
- Single laptop; k6, the app, PostgreSQL and Redis share the same 8 CPUs, so absolute ceilings are lower than on separate machines. Only the before/after comparison on the same setup is meaningful.
- The traffic model is synthetic (skewed toward popular cities and nearby dates). Real hit ratios depend on real traffic.
- A cached result can be up to one TTL (30 s plus or minus 20%) old. Booking never reads the cache, so this can show a hotel that then answers 409, never cause an overbooking (proved by `SearchCacheIT`).
- Hotel info (`/hotels/{id}/info`) is not cached; its latency in these runs is unaffected by the cache.
