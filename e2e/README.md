# End-to-end checks against the real stack

Four browser scripts, all headless Playwright in Docker (nothing installed on your machine):

- `stripe-real-flow.js`: payments with real Stripe (sandbox), described below.
- `frontend-journey.js`: **the whole product in one run.** A manager signs up and lists a hotel through the UI (place picker, amenities,
  photo, room type, put on sale); a guest finds it, reserves, names a guest, pays on the real Stripe checkout page with the test card and
  sees the confirmation, then cancels and is refunded (the script checks that Stripe refunded exactly the amount shown); the manager sees the
  booking and changes prices. Needs the real Stripe sandbox keys in `.env`.
- `frontend-browse.js`: search, results with paging, the hotel page, sold-out and not-found states, and phone-width layout. It creates its
  own hotels through the API first (12 in one city, plus a fully booked one). Run it exactly like `frontend-auth.js` below, replacing the
  script name.
- `frontend-auth.js`: the web app's sign-up, sign-in, session restore on reload and sign-out, against the running stack. Needs only
  `docker compose up --build` (no Stripe keys). Run (Git Bash on Windows needs `MSYS_NO_PATHCONV=1`):

  ```bash
  docker run --rm -v "$PWD/e2e":/w -w /w mcr.microsoft.com/playwright:v1.49.1-noble bash -c 'npm i --silent playwright@1.49.1 >/dev/null 2>&1; export WEB_HOST=$(getent hosts host.docker.internal | cut -d" " -f1); node frontend-auth.js'
  ```

---

## Before running the browser scripts

The browser scripts create hotels, so start the stack with synthetic places (any place id works and searching a name returns a place of
that name, so no script depends on the Internet or on a real city): `PLACES_PROVIDER=synthetic docker compose --profile stripe up -d --build --wait`.
The synthetic provider refuses to start with the `prod` profile. A normal `docker compose up` uses the real provider.

---

# Real Stripe (sandbox)

`stripe-real-flow.js` pays for a booking on Stripe's hosted checkout with Stripe's **test cards**, then checks the whole chain:
the real webhook, confirmation, cancel with a real refund (through the Kafka refund worker), a declined card, and an expired session.
It never uses real money: it refuses to run unless `STRIPE_SECRET_KEY` starts with `sk_test_`.

It is not part of `mvn verify` (it needs Stripe keys and the running stack). Nothing is installed on your machine: it runs in the
official Playwright Docker image with its own headless browser, so no Stripe login is needed.

## Prerequisites

1. `.env` with a Stripe **sandbox** key and webhook secret (see `.env.example`):
   `STRIPE_SECRET_KEY=sk_test_...` and `STRIPE_WEBHOOK_SECRET=whsec_...`
   (the secret: `docker run --rm stripe/stripe-cli:v1.55.11 listen --print-secret --api-key <sk_test_...>`).
2. The stack with real payments and the webhook forwarder: `docker compose --profile stripe up -d --build --wait`
   (`PAYMENTS_FAKE` must be `false`).

## Run

```bash
# Git Bash on Windows needs MSYS_NO_PATHCONV=1
docker run --rm --env-file .env -v "$PWD/e2e":/w -w /w mcr.microsoft.com/playwright:v1.49.1-noble \
  bash -c "npm i --silent playwright@1.49.1 > /dev/null 2>&1; node stripe-real-flow.js"
```

Each step prints `PASS` or `FAIL`, and the exit code is non-zero if any step failed. Set `API_BASE` if the API is not at
`http://host.docker.internal:8080/api/v1`.

Limits: the Stripe checkout page has no stable selectors, so the script clicks at fixed positions; if Stripe changes its layout,
update `payOnCheckout`. It leaves test data (an "E2E Stripe Hotel ...") in the database. The Stripe sandbox is a shared external
service, so a failure can also mean Stripe is slow or changed, not that AirStay is broken.
