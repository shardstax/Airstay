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

Each file says which data, machine and script it used, and what it does not show.
