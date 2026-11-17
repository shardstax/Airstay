# Load tests

Measured performance numbers for AirStay come from here, nowhere else. Everything runs in Docker (k6 included), so
nothing needs installing besides Docker.

## 1. Seed realistic data

The API would take minutes to create this much data (activation saves 366 inventory rows per room one by one), so the
data is inserted with SQL:

```bash
docker compose up --build -d --wait
docker compose exec -T postgres psql -U airstay -d airstay -v hotels=300 -v cities=10 -v rooms=4 < load-tests/seed.sql
# remove it again:
docker compose exec -T postgres psql -U airstay -d airstay < load-tests/unseed.sql
```

Default scale: **300 hotels, 1,200 room types, 439,200 inventory rows (one year of nights), 109,800 nightly
"from" prices**, about 105 MB of inventory. About 2% of seeded nights are sold out and 25% are partly booked, so the
availability filter does real work.

## 2. Run k6

```bash
# bash (Git Bash on Windows needs MSYS_NO_PATHCONV=1)
docker run --rm -i --network airstay_default -e BASE_URL=http://app:8080/api/v1 \
  -e RATE=100 -e LABEL=my-run -v "$PWD/load-tests:/scripts" grafana/k6:latest run /scripts/search.js
```

| Variable | Meaning | Default |
|---|---|---|
| `EXECUTOR` | `rate` = open model (fixed requests/s, honest latency), `vus` = closed model (max throughput) | `rate` |
| `RATE` | iterations per second in `rate` mode (each = 1 search, 25% also open a hotel) | 100 |
| `VUS` | virtual users in `vus` mode | 50 |
| `DURATION` / `WARMUP` / `WARMUP_RATE` | measured period / warm-up that is excluded from the results / its request rate | 60s / 30s / 100 |
| `LABEL` | name of the raw result file `results/<LABEL>.json` (git-ignored) | run |

Traffic model (synthetic): city and start date are skewed toward a few popular values (a squared random number), stays
are 1 to 3 nights, 25% of searchers open one of the hotels they found. Cache hit rates depend on this model, so any
cache result is only as realistic as it is.

## 3. Activation and the hourly price refresh (counting SQL statements)

Two operations are measured by how long they take **and by how many SQL statements they send**, because their cost is round
trips between the application and the database, not time inside PostgreSQL. For the statement counts, start the stack with
the benchmark override (it adds `pg_stat_statements`):

```bash
docker compose -f docker-compose.yml -f load-tests/docker-compose.bench.yml up --build -d --wait
bash load-tests/db-statements.sh enable          # once per database
docker compose exec -T postgres psql -U airstay -d airstay -v hotels=300 -v cities=10 -v rooms=4 < load-tests/seed.sql
```

**Activating a hotel** (`activation.js`: one user creates a hotel with `ROOMS` room types, then times the activation request):

```bash
bash load-tests/db-statements.sh reset
docker run --rm -i --network airstay_default -e BASE_URL=http://app:8080/api/v1 -e ROOMS=4 -e ITERATIONS=15 \
  -e LABEL=activation -v "$PWD/load-tests:/scripts" grafana/k6:latest run /scripts/activation.js
bash load-tests/db-statements.sh show            # statements for the whole run (divide by ITERATIONS)
docker compose exec -T postgres psql -U airstay -d airstay < load-tests/unbench.sql    # remove the benchmark hotels
```

**The hourly price refresh** is timed by the job itself ("Price refresh finished: N hotels in X ms" in the app log). Make it run
every minute with `PRICING_REFRESH_CRON="0 * * * * *"` (set it before `docker compose up`), wait for two finished runs, and
count the statements of the second (steady state): `reset` right after the first finish line, `show` right after the second.
Take care that no other load runs at the same time.

## 4. How to read the output

`p95` is the latency 95% of requests beat. `dropped iterations` means k6 could not start requests fast enough
because the system was saturated: latency numbers at that rate are then a lower bound, not a result. Always record
the machine (CPUs, memory, Docker) with a result: see `results/*.md`.

## 5. Recorded results

- `results/baseline.md` and `results/with-cache.md`: search before and after the Redis cache.
- `results/phase5-activation-and-price-refresh.md`: hotel activation and the hourly price refresh before and after, with
  statement counts and an index review.
- `results/phase6-booking-load.md`: booking contention (zero overbooking), the throughput ladder and the connection-pool test.

Each file says which data, machine and script it used, and what it does not show.

## 6. Booking flow and the overbooking proof

`booking-flow.js` drives the whole booking (search, hotel page, hold, guests, payment, signed webhook, confirm, some abandon,
some cancel). `run-booking-load.sh` runs it, then checks PostgreSQL for overbooking and samples database activity and
container CPU. It creates its own hotels and users through the API ("Load Hotel <run id> n") and needs:

- **Fake payments, no rate limit and synthetic places**, otherwise you measure Stripe, the rate limiter and the geocoding provider (hotels are created in real places, which needs the Internet and a real place id), not the booking code:
  `PAYMENTS_FAKE=true RATE_LIMIT_ENABLED=false PLACES_PROVIDER=synthetic docker compose up -d --build --wait app`
  (the fakes refuse to start with the `prod` profile). The scripts create hotels with a made-up `placeId` and room types from the catalog (`Standard`, `Deluxe`, ...). Use `--build`: a stale image silently keeps the real provider.
- The webhook secret is read from the running app, so the signed events pass the real signature check.

```bash
bash load-tests/run-booking-load.sh contention VUS=100 USERS=100 DURATION=30s LABEL=contention    # one hot room, closed model
bash load-tests/run-booking-load.sh mixed RATE=60 HOTELS=20 WARMUP=20s DURATION=60s LABEL=mixed-60 # realistic mix, open model
# remove a run's hotels, bookings and users (the run id is printed at the end):
docker compose exec -T postgres psql -U airstay -d airstay -v run=<RUN_ID> < load-tests/unload.sql
```

Variables (`KEY=value` after the mode): `RATE` new shoppers per second, `HOTELS`, `CITIES`, `ROOMS` room types per hotel, `STOCK`
rooms per type, `DAYS` booking window, `USERS`, `ABANDON`, `CANCEL`, `WARMUP`, `DURATION`; contention only: `VUS`, `STOCKS`
(rooms per type, comma separated). The script exits non-zero on any 5xx, unexpected answer, failed check or database violation.

**The proof** is `verify-no-overbooking.sql`, run by the script: no night where booked plus reserved exceeds the rooms that
exist, and the inventory counters equal what the booking rows imply (CONFIRMED is booked; RESERVED, GUESTS_ADDED and
PAYMENTS_PENDING are reserved).

Pitfalls: a JVM that has just restarted is slow for a minute under heavy load, so warm it or discard the first run; fixed stock
drains faster at higher rates, so raise `HOTELS` with `RATE`; k6 shares the machine with the app, so treat numbers as relative.
