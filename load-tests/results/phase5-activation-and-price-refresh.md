# Hotel activation and the hourly price refresh, before and after (Phase 5)

Measured 2026-11-17 with the tools in this folder (`activation.js`, `db-statements.sh`, the job's own timing log) on the same
machine and the same data before and after. No decision rule was written down before measuring; the change is kept because it
removes a structural cost (one statement per night) that the statement counts show clearly.

## Machine and data

Windows 11 laptop, 8 CPUs, Docker Desktop 29.6.2 with about 3.7 GiB of memory, PostgreSQL 16 in Docker, the API in Docker with
default JVM settings (a container-aware heap), Redis and Kafka running. Data: the seed in [`seed.sql`](../seed.sql) (300 hotels,
1,200 room types, 439,200 inventory rows) plus 7 hotels from earlier smoke tests plus 30 benchmark hotels (15 with 4 room types,
15 with 1), **337 hotels and 469,212 inventory rows** for the refresh measurements.

## How the numbers were taken

- **Time:** activation is the duration of `PATCH /admin/hotels/{id}/activate` as seen by k6 (one user, one hotel at a time, 15
  runs per room-type count). The refresh is the duration the job logs about itself ("Price refresh finished: N hotels in X ms"),
  run every minute with `PRICING_REFRESH_CRON`; **run 2 is steady state** (the first run after a restart also pays JVM warm-up).
- **Statements:** PostgreSQL's `pg_stat_statements` (top-level statements only, so PostgreSQL's own foreign-key checks are not
  counted), reset right before the operation. For the refresh, counters were reset right after run 1 finished and read right after
  run 2 finished. The outbox relay's background polling is excluded. A statement count includes `BEGIN` and `COMMIT`.
- **Activation statements** also include creating the benchmark hotel and its rooms (about 5 to 8 statements per run), which is
  why the "after" count is not zero-based.

## What was slow

- **Activation** saved one `inventory` row per night per room type, one INSERT each (366 per room type; the IDENTITY ids also
  prevent JDBC batching). Then it ran the price refresh for the hotel.
- **The price refresh** found each hotel's "from" price with **one query per night** (366 per hotel) and wrote it with one more
  statement per night, loaded one room at a time, loaded whole hotels (each eagerly loads its owner and the owner's roles) and wrote
  each changed price on its own. About **373 statements per hotel even when nothing changed**.

## What changed

1. Activation creates a room's nights with one `INSERT ... SELECT generate_series(...)`.
2. The refresh pages hotel ids only; reads a hotel's nights with their rooms in one query; computes prices in memory; locks the rows
   that change in id order (the same lock order the booking code uses, so refresh and bookings cannot deadlock); updates them with one
   `UPDATE ... FROM unnest(...)`; and upserts the "from" prices with one `INSERT ... ON CONFLICT DO UPDATE ... WHERE price <> ...`.
   Both writes touch only rows whose price changed, so a refresh in which nothing changed writes nothing.

## Results

### Activating a hotel (15 runs each)

| Room types | | Median | p95 | min / max | Statements per activation |
|---|---|---|---|---|
| 4 | before | 1,008 ms | 1,821 ms | 827 / 2,550 ms | 2,282 (34,233 for 15 runs) |
| 4 | **after** | **124 ms** | **240 ms** | 97 / 331 ms | **39** (589 for 15 runs) |
| 1 | before | 393 ms | 462 ms | 308 / 482 ms | 1,128 (16,927 for 15 runs) |
| 1 | **after** | **44 ms** | **54 ms** | 39 / 58 ms | **21** (312 for 15 runs) |

Median **8.1 times** faster for 4 room types (8.9 times for 1); statements **58 times** fewer (54 times). The 21,960 INSERTs of
the "before" run for 4 room types are exactly 15 x 4 x 366: one per night per room.

### The hourly price refresh (337 hotels, 469,212 inventory rows)

| | Time | Statements | Statements per hotel |
|---|---|---|---|
| Before, steady state (run 2) | 111,564 ms | 125,621 | 373 |
| **After, steady state (run 2)** | **5,785 ms** | **1,133** | **3.4** |
| Before, first run after a restart | 149,135 ms | not counted | not comparable (see below) |
| After, first run after a restart | 6,884 ms | not counted | not comparable (see below) |
| **After, stress: every price overwritten with 1.00 first** | 11,347 ms | 1,817 | 5.4 |

Steady state is **19.3 times** faster with **111 times** fewer statements. In the stress run the job had to rewrite all 469,212
prices (one lock statement and one update per hotel) and still finished in 11.3 s; afterwards no price was left at 1.00. The
"before" stress case was not measured.

The most frequent statements, steady state after: 337 reads of a hotel's nights (1.5 s of database time) and 337 "from" price
upserts that wrote no row (1.9 s), plus transaction control. Before: 123,342 single-night "from" price lookups (2.8 s of database time).

## What these numbers do and do not say

- **Database time did not change** (3.6 s before, 3.5 s after for the refresh): the saving is **round trips between the
  application and the database** and the ORM work around them (125,000 statements became 1,100), not faster SQL.
- The activation and refresh numbers are from one laptop. Treat them as a ratio, not as production latency.
- **Neither operation is on a user's request path.** Activation is a rare manager action (about 1 s became about 0.1 s) and the
  refresh runs in the background once an hour. The benefit is much less database and CPU load, and a refresh whose duration grows
  slowly with the number of hotels. As a **linear extrapolation, not a measurement**: before, about 0.33 s per hotel would put a
  10,000-hotel catalogue at roughly 55 minutes per hourly run; after, about 17 ms per hotel is roughly 3 minutes.
- The "first run after a restart" rows are not a clean comparison: the "before" run started from freshly seeded prices (many
  rows to write) while the "after" run started from prices already settled by earlier runs. Only steady state is compared.
- Prices must not change: the pricing integration tests check the rules (surge, occupancy, last-minute, "from" price = cheapest
  room), that only changed rows are written and that a second refresh writes nothing.

## Indexes: checked, nothing to add

`EXPLAIN (ANALYZE, BUFFERS)` on the same data (496,662 inventory rows, 134,322 "from" prices), hot paths:

| Query | Plan | Execution time |
|---|---|---|
| Price refresh: every night of one hotel (1,464 rows) with its rooms | bitmap index scan on `unique_hotel_room_date` | 2.2 ms |
| Booking: lock a room's free nights | index scan on `idx_inventory_room_date` | 0.15 ms |
| Hotel page: average price per room type for a stay | index scan on `unique_hotel_room_date` | 0.5 ms |
| Search: hotels in a city free on every night (uncached) | `idx_inventory_city_date`, `unique_hotel_min_price_date`, `hotel_pkey` | 3.3 ms |

One foreign-key column has no index: `booking.room_id` (used when deleting a room and by `existsByRoom`). In a transaction that was
rolled back, with 200,000 synthetic bookings, checking a room that has none took **7.7 ms without an index and 0.05 ms with one**.
That is a rare manager action and a small absolute cost, so **no index was added**; revisit it if bookings reach the millions or
room deletion becomes frequent.

## Reproduce

See [section 3 of the load-test README](../README.md#3-activation-and-the-hourly-price-refresh-counting-sql-statements). Raw k6
results (`results/*.json`) are git-ignored.
