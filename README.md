# AirStay

![CI](https://github.com/shardstax/Airstay/actions/workflows/ci.yml/badge.svg)

Hotel booking backend built with Spring Boot: hotel search with live availability, per-night inventory,
time-limited room holds, layered dynamic pricing and Stripe Checkout payments.

## Tech stack

Java 21 · Spring Boot 3.5 · Spring Data JPA (Hibernate) · Spring Security + JWT · PostgreSQL · Redis · Flyway ·
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

## Running with Docker (recommended)

Only Docker is required; the Java build happens inside the image.

```bash
docker compose up --build        # PostgreSQL 16 + AirStay, ready when both health checks pass
docker compose down              # stop (add --volumes to also delete the database)
```

- API: `http://localhost:8080/api/v1` · Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
- Health: `http://localhost:8080/api/v1/actuator/health`
- It starts with local-only defaults. To take test payments, copy `.env.example` to `.env`, add your
  Stripe test key and run `docker compose --profile stripe up --build`. The Stripe CLI container prints a
  `whsec_...` secret: put it in `.env` as `STRIPE_WEBHOOK_SECRET` and restart the app.

The image is a multi-stage build: Maven compiles in a JDK image, the jar is split into layers (code changes
rebuild only the last layer), and the runtime is a ~150 MB JRE-only Alpine image running as a non-root
user. Profiles: `dev` (default in compose, debug logs) and `prod` (JSON logs, proxy headers).

## Running without Docker

Requirements: JDK 21, PostgreSQL 16+ with an empty database, Stripe test keys, optionally the Stripe CLI.
Docker is only needed for the integration tests.

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
./mvnw test     # unit tests only: fast, no Docker needed
./mvnw verify   # unit + integration tests (needs Docker) + coverage report in target/site/jacoco
```

**110 automated tests** (63 unit + 47 integration).

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
- **CI**: GitHub Actions runs `./mvnw verify` on every push and pull request, then builds the Docker image,
  starts the compose stack and smoke-tests the running API.

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
