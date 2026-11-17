# Phase 6: booking-flow load test and overbooking proof

Measured on one laptop (8 CPUs and about 3.7 GiB given to Docker), with the app, PostgreSQL, Redis, Kafka **and k6 all on the
same machine**, so these are not production numbers. One app instance, default settings (Hikari pool 10, Tomcat 200 threads).
Payments use the in-memory fake provider (`PAYMENTS_FAKE=true`), the rate limiter is off (`RATE_LIMIT_ENABLED=false`), and the
webhook is a correctly signed Stripe-style event. **Nothing here measures Stripe itself.**
Each run's compact summary is committed as `load-tests/results/<label>.summary.json` (`contention-base`, `mixed-10`, `mixed-30`,
`mixed-60`, `mixed-100`, `mixed-100-h60`, `mixed-100-h60-pool30`, `mixed-100-h60-cold10`). The full k6 dumps are not committed.

Each shopper runs: search, open a hotel, hold a room (`/bookings/init`), add guests, start payment, signed webhook, check
`CONFIRMED`. 10% abandon after the hold, 5% cancel after confirming. Open model (new shoppers arrive at a fixed rate whatever
the response times), 20 s warm-up excluded from the percentiles, then 60 s measured.

Commands (the script's own defaults differ: 30 s warm-up, 120 s mixed, 45 s contention):

```bash
bash load-tests/run-booking-load.sh contention VUS=100 USERS=100 DURATION=30s LABEL=contention-base
bash load-tests/run-booking-load.sh mixed RATE=<10|30|60|100> WARMUP=20s DURATION=60s LABEL=mixed-<rate>     # 20 hotels, 60 users
bash load-tests/run-booking-load.sh mixed RATE=100 HOTELS=60 WARMUP=20s DURATION=60s LABEL=mixed-100-h60     # stock not the limit
```

The mixed runs use the default 60 users, 3 cities, 3 room types per hotel with 4 rooms each, and a 30-day booking window.

## 1. Contention: everyone wants the same room for the same nights

100 virtual users loop for 30 s over 1 hotel with 3 room types holding 5, 10 and 20 rooms (35 rooms), all asking for the same two nights.

| | |
|---|---|
| Hold attempts | 10,317 |
| Holds created and confirmed | **35 (exactly the stock)** |
| Clean "not available" answers (409) | 10,282 |
| Lock conflicts ("resource busy"), 5xx, unexpected answers | 0, 0, 0 |
| Overbooked nights / counters differing from the booking rows / negative counters | **0 / 0 / 0** |
| `init` latency p50 / p95 / p99 | 257 ms / 645 ms / 876 ms |

From PostgreSQL after the run: 35 bookings, all CONFIRMED, 35 rooms; 6 nights fully sold out (3 room types x 2 nights); the fullest night held 20 of 20 rooms.
The "10,282 clean 409s" are k6's count of answers saying "not available" (any other 409 is counted as an unexpected answer).

## 2. Mixed flow, arrival-rate ladder (20 hotels, 60 room types, stock 4 each)

| New shoppers/s | Requests/s | search p95 | init p95 | webhook p95 | 5xx | Overbooking | Dropped iterations |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 65 | 9.7 ms | 14.3 ms | 9.9 ms | 0 | 0 | 0 |
| 30 | 184 | 5.9 ms | 12.8 ms | 10.5 ms | 0 | 0 | 0 |
| 60 | 323 | 8.5 ms | 25.9 ms | 19.7 ms | 0 | 0 | 0 |
| 100 | 452 | 164 ms | 981 ms | 640 ms | 0 | 0 | **203** |

At 60/s every one of the 7 step types has p95 under 26 ms (search 8.5, info 11.1, init 25.9, addGuests 19.3, payments 13.3, webhook 19.7,
status 9.0 ms; cancel had too few calls, 126, to matter). At 100/s the offered load was **not fully delivered** (203 shoppers could not be
started, so this is no longer a clean open-model result). The app uses 200 to 315% CPU (of 800%) and PostgreSQL has at most 7 active queries
and never a lock wait. Stock in these runs is fixed, so at higher rates more shoppers find nothing to book (at 100/s, 917 of the 21,960
inventory nights ended fully sold out; this is the `nights_sold_out` column of the check, not a count of shoppers).

Repeat at 100/s with 60 hotels so stock is not the limit, **warm JVM**: 540 requests/s, init p95 353 ms, webhook p95 227 ms,
search p95 54 ms, 36 dropped iterations, 0 errors, 0 overbooking.

## 3. Did the connection pool limit it? No evidence it did

Hypothesis: the default pool of 10 connections is the bottleneck at 100/s. Test: same load with `maximum-pool-size=30`.

| 100/s, 60 hotels | JVM | init p50 / p95 | dropped |
|---|---|---:|---:|
| pool 10 | warm | 22 ms / 353 ms | 36 |
| pool 30 | **cold (just restarted)** | 3.7 s / 5.6 s | 1,909 |
| pool 10 (control) | **cold (just restarted)** | 2.8 s / 6.2 s | 1,589 |

The pool-30 run looked far worse only because the JVM had just restarted. The control with the default pool on a cold JVM
is just as slow, so between the two cold runs pool size made no measurable difference. There is no warm run with pool 30, and each
arm is a single run, so this only fails to show that the pool is the bottleneck; it does not prove it is not. **No setting was changed.**

## 4. What this shows, and what it does not

- Under contention and under a realistic mix, **no night was ever oversold and the inventory counters always matched the booking rows**
  (checked in PostgreSQL by `verify-no-overbooking.sql` after every run). Losers get a clean 409, never a 5xx.
- On this machine one instance is comfortable to about 60 new shoppers/s (about 320 requests/s, p95 under 26 ms for every step). At 100/s
  latency rises, there are still no errors, but k6 could not start every shopper (36 to 203 dropped iterations). The limit looks like CPU
  shared with k6, PostgreSQL and Kafka, **not** proven.
- A cold JVM hit directly with about 100 shoppers/s is very slow for the first minute or more; the 20 s low-rate warm-up is not
  enough right after a restart. Real deployments would warm up before taking traffic.
- Not measured: real Stripe, more than one app instance, a separate machine for k6, a larger database.
