# Deploy AirStay on Contabo

Use Ubuntu with Docker Engine and the Compose plugin. Deploy `chore/prod-deploy-data`.
This deployment runs AirStay, PostgreSQL, Redis, Kafka, and the refund worker on one VPS.
The notification service uses local Mailpit for demo email. It does not deliver to public inboxes.

## Bring up the site

1. Assign an available hostname to the VPS's public IPv4 address with an `A` record. A Duck DNS hostname works for a demo. Open inbound TCP 80 and 443 for Caddy, and limit SSH access to the intended administrators.
2. From the deployment branch, copy `deploy/contabo/env.example` to the repository root as `.env`. Restrict file permissions to the deploy user. Set the hostname, a new database password, a fresh JWT signing secret of at least 32 bytes, and a restricted OpenAI project key.
3. Run the Compose stack. Caddy issues and renews the HTTPS certificate once DNS resolves publicly. PostgreSQL and Kafka keep their data in named volumes.

```bash
docker compose -f docker-compose.yml -f docker-compose.contabo.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.contabo.yml up -d --build --wait
```

With `DEMO_SEED_ON_STARTUP=true`, the API adds demo inventory and photos after Flyway runs on the fresh database. Restarts keep the same data.
For a real catalog or an existing database that has never been seeded, set `DEMO_SEED_ON_STARTUP=false`.
When changing hostnames, point both names at the VPS and set `AIRSTAY_LEGACY_HOST` to the previous name. Caddy redirects it to `AIRSTAY_HOST`.

## Verify the demo

- Check `/healthz` and `/api/v1/actuator/health/readiness` over public HTTPS.
- Search Pune, verify a seeded hotel photo, and test the AI assistant. Redis enforces the shared AI usage quota.
- For test checkout, configure Stripe test credentials and register the public `/api/v1/webhook/payment` endpoint for the checkout events listed in the [Railway runbook](railway.md#service-variables). Add the endpoint's signing secret to `.env` and rerun `docker compose -f docker-compose.yml -f docker-compose.contabo.yml up -d app` to apply it.
- Before storing any data you want to keep, configure and test an off-VPS PostgreSQL backup.
