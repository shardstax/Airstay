# AirStay

![CI](https://github.com/shardstax/Airstay/actions/workflows/ci.yml/badge.svg)

Hotel booking backend built with Spring Boot: hotel search with live availability, per-night inventory,
time-limited room holds, layered dynamic pricing and Stripe Checkout payments.

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Data JPA (Hibernate) · Spring Security + JWT · PostgreSQL · Redis · Kafka · Flyway ·
Stripe Checkout + webhooks · Bean Validation · springdoc OpenAPI · JUnit 5 · Mockito · Testcontainers ·
JaCoCo · k6 · GitHub Actions · Docker · Docker Compose · Spring Boot Actuator

## How booking works

Inventory is one row per room type per night with `totalCount`, `reservedCount` (held) and `bookedCount` (paid).

```
POST /bookings/init            RESERVED          rooms move into reservedCount, hold lasts 10 min
POST /bookings/{id}/addGuests  GUESTS_ADDED
POST /bookings/{id}/payments   PAYMENTS_PENDING  Stripe session (31 min); hold extended to 35 min
Stripe webhook (paid)          CONFIRMED         reservedCount -> bookedCount
POST /bookings/{id}/cancel     CANCELLED         bookedCount released, refund issued after commit
hold lapses / session expires  EXPIRED           reservedCount released by a scheduled job or webhook
```

Correctness rules:

- **No overbooking.** Inventory rows are locked (`SELECT ... FOR UPDATE`, always in date order to avoid
  deadlocks) before any count changes, every update checks it changed exactly one row per night, and a
  database `CHECK (booked + reserved <= total)` constraint backs it up.
- **Idempotent webhooks.** Stripe delivers events at least once. The booking row is locked and only a
  `PAYMENTS_PENDING` booking can become `CONFIRMED`, so a repeated event changes nothing. Payment that
  arrives for an expired or cancelled booking is refunded.
- **Holds always end.** Unpaid holds are released by a scheduled job; the Stripe session closes before
  the hold does, so a guest can never pay for rooms that were already released.
- **Dates.** `checkInDate` is the first night and `checkOutDate` is the departure day, so a 2-night stay
  is charged for 2 nights and the departure day stays bookable.
- **Refunds after commit.** Cancellation commits first; the Stripe refund runs afterwards with an
  idempotency key, so a refund can never happen for a booking that is still active.
- **Safe retries (`Idempotency-Key`).** A client that times out can repeat `POST /bookings/init` with the same
  `Idempotency-Key` header and gets the *same* booking back (header `Idempotent-Replayed: true`) instead of a second
  hold. The same key with a different request is refused (409). The key is saved in PostgreSQL in the **same
  transaction** as the booking (`INSERT ... ON CONFLICT DO NOTHING` makes simultaneous retries wait for the first
  one), so a crash can never leave a booking without its key or the reverse. A failed attempt (for example sold out)
  rolls the key back, so it can be retried. Keys expire after 24 hours. This is deliberately not in Redis: Redis cannot
  take part in the booking transaction.

## Pricing

Decorator chain, applied per night: base price → owner surge factor → occupancy (+20% above 80% booked)
→ urgency (+15% within 7 days) → holiday (+25% on configured dates). Stored nightly prices refresh hourly
and immediately after an owner changes a room price or surge factor.

## Security

- Short-lived access token (10 min) in the `Authorization` header; refresh token (180 days) in an
  httpOnly, Secure, SameSite cookie scoped to `/api/v1/auth`. Tokens carry a type claim, so a refresh
  token cannot be used as an access token.
- Deny-by-default rules: only auth, webhook, docs and `GET /hotels/**` are public; `/admin/**` needs
  `HOTEL_MANAGER`. Every hotel, room, inventory, guest and booking operation checks ownership.
- CORS is enforced by Spring Security for the configured frontend origins.
- Rate limiting (see below): login, sign-up and booking attempts are limited per e-mail, address or user, answered
  with `429` and a `Retry-After` header.

## Redis: what it is used for, and what it is not

Redis is an **optional accelerator**, never the source of truth. PostgreSQL decides what is available and what is booked.

| Use | What it does | Measured effect |
|---|---|---|
| **Search cache** | Caches `GET /hotels/search` results for 30 s (plus or minus 20%) | Capacity **945 to 2,119 searches/s (2.24x)**; at 600 searches/s p95 **82 ms to 10 ms** with no dropped requests ([results](load-tests/results/with-cache.md)) |
| **Rate limiting** | Counts requests per key in Redis (atomic Lua script), shared by every app instance | Login: 10/min per e-mail and 30/min per address; sign-up: 10/min per address; booking: 20/min per user |

- **Why the cache is safe:** booking never reads it, it locks the inventory rows in PostgreSQL. A cached result can be at most
  one TTL old, so at worst it lists a hotel that then answers 409. It can never cause an overbooking (`SearchCacheIT`).
- **If Redis is down:** nothing fails. The cache falls back to PostgreSQL and the limiter lets requests through. A circuit breaker
  skips Redis for 10 s after three failures, so a dead Redis costs two or three slow requests and then nothing (checked live
  by stopping the container).
- **Not used for:** inventory counts or room holds (they must be transactional with bookings in PostgreSQL), distributed locks,
  or idempotency keys (see above).
- **Honest limits:** at 100 searches/s search was already fast (p95 about 6 ms), so only the median improves there; the gain is
  capacity headroom. Fixed-window rate limiting allows up to twice the limit across a window boundary.
- Settings: `SEARCH_CACHE_ENABLED`, `RATE_LIMIT_ENABLED`, `REDIS_HOST`, `REDIS_PORT` (see `.env.example`).
  Measure it yourself with [`load-tests/`](load-tests/README.md) (k6 in Docker, SQL seed for 439,200 inventory rows).

## Kafka: reliable events, refunds and e-mail

**Why Kafka is here (the honest version).** Before, a failed Stripe refund was only logged for someone to retry by hand.
The standard fix is a **transactional outbox**, and on its own that would already make refunds reliable. Kafka earns its
place because the same facts have **two independent consumers**: AirStay's own refund worker and a separate notification
service. Neither needs to know the other exists, and a third consumer would need no change to the booking code.

```
booking change + outbox row  ──one database transaction──▶  PostgreSQL
OutboxRelay (every 500 ms)   ──publishes in order; marks a row published only after Kafka acknowledged it──▶  Kafka
Kafka  ──▶  RefundWorker (in the API)         ──▶  Stripe refund, retried without blocking, then a dead-letter topic
       ──▶  notification-service (separate)   ──▶  e-mail (Mailpit locally)
```

- **Outbox.** Every status change (`BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `BOOKING_EXPIRED`, `REFUND_REQUESTED`,
  `REFUND_COMPLETED`) is saved in the `outbox_event` table **in the same transaction** as the change itself (the service refuses
  to run outside a transaction). The relay claims rows with `FOR UPDATE SKIP LOCKED`, publishes in id order and stops at the
  first failure, so order is kept. Delivery is **at-least-once**: each event has a unique `eventId` and consumers ignore ids
  they have seen. The contract is [`docs/events.md`](docs/events.md) (only ever add fields).
- **Refunds.** Cancelling (or a payment arriving for an expired booking) records `BOOKING_CANCELLED` and `REFUND_REQUESTED` with
  the status change; the request thread no longer calls Stripe. `RefundWorker` retries a failed refund through retry topics
  (non-blocking: a Stripe outage does not stall other refunds): 6 attempts, delays 10 s, 30 s, 90 s, 270 s, 600 s, then the
  dead-letter topic `airstay.refund-requests.DLT` with an `ERROR` log. Stripe gets an idempotency key per booking, so repeating a
  refund that already succeeded changes nothing.
- **Notification service** ([`notification-service/`](notification-service/), its own Maven project and Docker image; it shares
  no code with the API, only the event contract). It e-mails the guest for confirmed, cancelled and expired bookings and
  completed refunds, using only the event's own snapshot (it never calls the API). It is **idempotent** (an e-mailed `eventId` is
  remembered in Redis, but only after the send succeeded, so a failed send is retried, never lost), **ignores unknown event
  types and unknown fields** (the API can evolve), sends **malformed events straight to a dead-letter topic**, retries SMTP
  failures through retry topics (5 attempts, 5 s to 135 s) and then dead-letters to `airstay.booking-events.notifications.DLT`.
  It never logs e-mail addresses (a test checks the log output).

**What happens when something is down** (each case has an automated test, and the first two were also run live on the Compose stack):

| Down | What happens |
|---|---|
| **Kafka** | Bookings, cancellations and webhooks keep working. Events wait in the `outbox_event` table and are published when Kafka returns. Live run: webhook answered `204`, booking `CONFIRMED`, 1 event waited in the outbox and no e-mail was sent; after Kafka came back exactly 1 e-mail arrived and the outbox emptied. |
| **Notification service** | Bookings are unaffected. Events wait in Kafka; after a restart the e-mail arrives (live run: none while it was down, exactly 1 afterwards). |
| **Mail server** | The message moves through the retry topics (other messages are not blocked), then to the dead-letter topic. |
| **Redis** | The notification service may send a duplicate if an event is delivered twice; it never loses an e-mail. The API falls back to PostgreSQL (see above). |
| **Stripe** | The refund is retried for about 17 minutes, then dead-lettered. |

**Honest limits.** One Kafka broker without replication (a local setup, not a production cluster); at-least-once, not
exactly-once (so the consumers are idempotent); ordering is guaranteed per booking only; e-mail amounts are shown in INR because
the contract has no currency field yet; the e-mails are plain text; a dead-lettered message needs a person (read it with the
commands in [`docs/events.md`](docs/events.md)). No throughput or latency numbers are claimed for Kafka: none were measured.

Look at it: `docker compose up --build -d --wait`, then run [`scripts/smoke-events.sh`](scripts/smoke-events.sh) (it confirms a
booking with a Stripe webhook signed the way Stripe signs it, then waits for the confirmation e-mail and, after a
cancellation, the cancellation e-mail) and open the Mailpit inbox at `http://localhost:8025`.

## Performance: what was measured, and what it does not show

Every number below comes from [`load-tests/`](load-tests/README.md) (k6 and `pg_stat_statements` in Docker, on a laptop), measured
before and after on the same data. Treat them as ratios, not as production latency.

| Operation | Before | After | Why |
|---|---|---|---|
| Search, 100 concurrent users (Redis cache) | 945 searches/s | **2,119 searches/s** (2.24x) | cache-aside, 30 s TTL |
| Activate a hotel with 4 room types (median) | 1,008 ms | **112 ms** (9.0x) | one `INSERT ... generate_series` instead of 366 per room type, and a set-based price refresh |
| SQL statements for that activation | 2,282 | **40** (57x fewer) | same |
| Hourly price refresh, 337 hotels (469,212 inventory rows) | 111.6 s | **6.0 s** (18.7x) | one read and at most two set-based writes per hotel instead of about 370 statements |
| SQL statements for that refresh | 125,621 | **1,470** (85x fewer) | same |

- **The saving is round trips, not faster SQL:** PostgreSQL's own execution time for the refresh was 3.6 s before and after. The refresh
  runs in the background, so what improves is database and CPU load and how its duration grows with the number of hotels.
- **Safe under concurrency:** refreshes of one hotel are serialized by an advisory lock, rows are locked in the same id order the
  booking code uses, and owner changes refresh after their own transaction commits, so none of them can deadlock with a booking
  (the concurrency tests still pass: 25 simultaneous requests for the last room give exactly one booking).
- **Indexes were reviewed with `EXPLAIN` on the real data:** every hot query already uses one; the one unindexed foreign key
  (`booking.room_id`) was measured (7.7 ms against 0.05 ms at 200,000 bookings) and deliberately left alone.
- Details, the method and the limits: [`load-tests/results/`](load-tests/results/phase5-activation-and-price-refresh.md).

**Booking under load** (fake payment provider, one laptop, k6 on the same machine):

- **No overbooking under contention:** 100 users fought for 35 rooms for the same two nights. Exactly 35 bookings were confirmed and the other
  10,282 attempts got a clean 409, with no lock conflicts and no 5xx. PostgreSQL showed 0 oversold nights and inventory counters equal to the
  booking rows; the same check passed after every mixed run.
- **Realistic flow** (search, hold, guests, payment, webhook, confirm, some abandon or cancel): about 320 requests/s at 60 new shoppers/s with
  p95 under 26 ms for every step and 0 errors. At 100/s latency rises (hold p95 0.35 to 1 s), errors stay at 0, but the load generator
  could not start every shopper (36 to 203 dropped iterations), so that rate is past this laptop's comfortable limit.
- **The connection pool was not shown to be the bottleneck:** between two cold-start runs, 30 connections instead of 10 made no
  measurable difference (single runs, so it is not proof), and it was left alone.
- Not measured: several app instances, a separate load generator. The load runs use a fake payment provider; real Stripe was checked separately (see Testing).
  Details: [`load-tests/results/phase6-booking-load.md`](load-tests/results/phase6-booking-load.md).

## Frontend

[`frontend/`](frontend/README.md) is a React + strict TypeScript web app (Vite, TanStack Query, Tailwind) in the same repository.
Its API types are generated from this backend's OpenAPI spec, and the Docker CI job fails if they drift. Lint (zero warnings), formatting, strict TypeScript and import boundaries are enforced in CI. It is served by Nginx, which also forwards
`/api` to the backend, so the browser uses one origin. It is being built in stages; so far: the foundation (typed client, design
basics, Docker and CI) and accounts (sign up, sign in, session restore, sign out). Open it at <http://localhost:5173> after `docker compose up --build`.

## Running with Docker (recommended)

Only Docker is required; the Java builds happen inside the images.

```bash
docker compose up --build -d --wait   # PostgreSQL 16, Redis 7, Kafka 3.9, Mailpit, the API and the notification service
docker compose down                   # stop (add --volumes to also delete the database and Kafka data)
```

- API: `http://localhost:8080/api/v1` · Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
- Health: `http://localhost:8080/api/v1/actuator/health`
- E-mails sent by the notification service land in the Mailpit inbox: `http://localhost:8025` (nothing leaves your machine).
- It starts with local-only defaults. To take test payments, copy `.env.example` to `.env`, add your
  Stripe test key and run `docker compose --profile stripe up --build`. The Stripe CLI container prints a
  `whsec_...` secret: put it in `.env` as `STRIPE_WEBHOOK_SECRET` and restart the app.

Each image is a multi-stage build: Maven compiles in a JDK image, the jar is split into layers (code changes
rebuild only the last layer), and the runtime is a JRE-only Alpine image running as a non-root user (API 173 MB,
notification service 120 MB). Profiles: `dev` (default in compose, debug logs) and `prod` (JSON logs, proxy headers).

## Running without Docker

Requirements: JDK 21, PostgreSQL 16+ with an empty database, Stripe test keys, optionally the Stripe CLI.
Docker is only needed for the integration tests. Redis and Kafka are optional to start the API: without Redis it serves from
PostgreSQL, and without Kafka the events wait in the outbox table (`EVENTS_ENABLED=false` stops the relay and the refund worker).

```bash
cp .env.example .env            # fill in the values
set -a && source .env && set +a # load them into the shell
./mvnw spring-boot:run
```

Flyway creates the schema on first start (`src/main/resources/db/migration`). Hibernate only validates it.

- API base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
- Stripe webhooks: `stripe listen --forward-to localhost:8080/api/v1/webhook/payment`

## Testing

```bash
./mvnw test                                      # API unit tests only: fast, no Docker needed
./mvnw verify                                    # API unit + integration tests (needs Docker) + coverage in target/site/jacoco
./mvnw -f notification-service/pom.xml verify    # the notification service (needs Docker for Kafka and Redis)
```

**213 automated tests**, all passing. The API has 152 (86 unit + 66 integration, **76.2%** line coverage) and the notification
service has 61 (52 unit + 9 integration, **97.8%** line coverage). Coverage was measured with JaCoCo on 2026-11-17; re-measure
before quoting it.

**Real Stripe sandbox check (2026-11-17, Playwright script in `e2e/`, not part of the automated suite):** against the Docker stack with a Stripe
test-mode key and the Stripe CLI forwarding real webhooks, a booking was paid on Stripe's hosted checkout with the test card 4242 4242 4242 4242
(webhook verified, booking CONFIRMED), cancelled (a real refund of INR 4,000 succeeded through the Kafka refund worker, and the cancellation and
refund e-mails arrived), a declined card (4000 0000 0000 0002) left the booking in PAYMENTS_PENDING, and expiring that checkout session released
the booking as EXPIRED. Only test-mode keys were used. Repeatable: [`e2e/`](e2e/README.md) (it passed all 8 steps headless in Docker on 2026-11-17).

- **Unit tests** (`*Test`, JUnit 5 + Mockito): every pricing rule and how they stack, JWT type and
  tamper checks, the booking state machine (holds, guests, payment, webhook idempotency, refunds
  after commit, expiry, cancellation, `Idempotency-Key` claim and replay), the search cache (hit, miss, TTL, Redis
  down, corrupt entry, 20 concurrent misses = 1 database query) and the Redis circuit breaker.
- **Integration tests** (`*IT`): the real app over HTTP against PostgreSQL 16 and Redis in Testcontainers, migrated
  by Flyway. Only Stripe is mocked; webhooks are sent with real Stripe signatures.
  - Full journey: search, hold, guests, payment, webhook, duplicate webhook, cancel and refund, with
    inventory counts checked in the database after every step.
  - Concurrency: 25 simultaneous requests for the last room give exactly one booking; 30 simultaneous
    overlapping stays never put any night over capacity and never deadlock.
  - Hold expiry, security rules (401/403, refresh-token misuse, ownership) and error responses.
  - Safe retries: the same `Idempotency-Key` twice gives one booking and one hold; 10 simultaneous retries give exactly
    one booking; a different request or a failed attempt behaves correctly.
  - Redis: a stale cached search never causes an overbooking; the rate limit is shared between two independent
    connections, exactly 10 of 50 simultaneous requests pass, and the window resets.
  - Events and refunds: with Kafka paused in the middle of a booking, the booking still works, its event waits in the
    outbox and arrives when Kafka returns; a refund is retried until Stripe recovers; a refund that never works ends in the
    dead-letter topic after the configured attempts; an unreadable request goes straight there.
  - Inventory and prices: activating a hotel creates every night once per room type with the room's values; prices follow the
    rules and the hotel's "from" price is its cheapest room; a refresh rewrites only the rows whose price changed and a second one
    rewrites none (judged by PostgreSQL's row version); the scheduled refresh repairs corrupted prices across several pages and
    skips hotels without inventory; a surge change, a room price change and deleting a room show the new prices when the owner's
    request returns.
  - Notification service (real Kafka and Redis, an in-process SMTP server): one e-mail per event with the right content;
    the same event twice sends one e-mail; unknown types are ignored; hopeless messages go to the dead-letter topic without
    retries; a failing mail server is retried until it is back, and one that never returns ends in the dead-letter topic without
    blocking other messages. A contract test parses the example in `docs/events.md`, and a log test checks that e-mail
    addresses never appear in the logs.
- **CI**: GitHub Actions runs `./mvnw verify` for the API and for the notification service on every push and pull request,
  then builds both Docker images, starts the whole compose stack and smoke-tests it, including
  [`scripts/smoke-events.sh`](scripts/smoke-events.sh) (signed Stripe webhook, outbox, Kafka, e-mail in Mailpit).

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/signup` (guest or `HOTEL_MANAGER`), `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Browse | `GET /hotels/search?city=&startDate=&endDate=&roomsCount=`, `GET /hotels/{id}/info?startDate=&endDate=` |
| Booking | `POST /bookings/init` (optional `Idempotency-Key` header), `/{id}/addGuests`, `/{id}/payments`, `/{id}/cancel`, `GET /{id}/status` |
| User | `GET/PATCH /users/profile`, `GET /users/myBookings`, CRUD `/users/guests` |
| Manager | `/admin/hotels` CRUD + `activate`, `bookings`, `reports`; `/admin/hotels/{id}/rooms`; `/admin/inventory/rooms/{id}` |

All responses use one envelope: `{ "timeStamp", "data", "error": { "status", "message", "subErrors" } }`.
Search is paged (`page`, `size` query parameters, both optional): `data` is
`{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`.
