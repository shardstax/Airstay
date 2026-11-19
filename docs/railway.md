# Deploy AirStay to Railway

Use the `chore/prod-deploy-data` branch for the public portfolio demo. Set a compute hard limit in the
Railway workspace before starting services. Railway pauses workloads when they reach the limit. OpenAI
API usage is billed separately.

## Services

Create these services in the same Railway project and environment:

| Service | Source and settings |
| --- | --- |
| Postgres | Railway PostgreSQL template, with its persistent volume and private network |
| Redis | Railway Redis template, with credentials and private networking |
| API | GitHub repo, branch `chore/prod-deploy-data`, root directory `/`, port `8080`, health path `/api/v1/actuator/health` |
| Frontend | Same GitHub branch, root directory `/frontend`, port `80`, health path `/healthz`, Railway public domain |

Expose the frontend publicly. Nginx proxies `/api/` to the API so the browser, Stripe webhooks, and secure
session cookies all use the same HTTPS origin. PostgreSQL, Redis, and the API can serve the app over
Railway's private network.

## Service variables

Set these on the API service. Railway reference variables use service names, so adjust `Postgres` and `Redis`
to match your project.

```dotenv
SPRING_PROFILES_ACTIVE=prod
PORT=8080
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_DATA_REDIS_URL=${{Redis.REDIS_URL}}
FRONTEND_URL=https://your-frontend.up.railway.app
CORS_ALLOWED_ORIGINS=https://your-frontend.up.railway.app
EVENTS_ENABLED=false
AI_SEARCH_ENABLED=true
AI_CHAT_MODEL_PROVIDER=openai
AI_SEARCH_DAILY_REQUEST_LIMIT=200
```

Add `JWT_SECRET_KEY` (a fresh random signing secret of at least 32 bytes), `STRIPE_SECRET_KEY` (a Stripe
test-mode key), `STRIPE_WEBHOOK_SECRET`, and `OPENAI_API_KEY` (a restricted OpenAI project key) as service
secrets. The default AI provider model is `gpt-4o-mini`. The API stops paid model requests when its Redis
quota cannot count them. **Site admin → Site settings** can pause or resume AI without a redeploy. Grant
the admin role using the procedure in the [README](../README.md).

Set these on the frontend:

```dotenv
PORT=80
AIRSTAY_API_UPSTREAM=http://${{API.RAILWAY_PRIVATE_DOMAIN}}:8080
AIRSTAY_FORWARDED_PROTO=https
```

Replace `API` with the backend service's name. Use Railway's Stripe test webhook URL
`https://your-frontend.up.railway.app/api/v1/webhook/payment` and subscribe to
`checkout.session.completed`, `checkout.session.async_payment_succeeded`,
`checkout.session.async_payment_failed`, and `checkout.session.expired`.
The lean demo omits Kafka and the notification service. Automated refunds and booking emails require
those services before you accept real payments.

## Seed the public demo

Wait for the API to apply Flyway migrations. Create a dedicated hotel-manager account in the public app
with a private password. Use a PostgreSQL client over an approved database connection with TLS. Set
`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGSSLMODE` in your shell, and use a local password file
or interactive password prompt for authentication.

```powershell
.\scripts\import-demo-photos.ps1 -Remote -OwnerEmail 'your-demo-manager@example.test'
```

This imports the fictional hotels, inventory, and photos together, using the existing manager account.
If you enabled a temporary Railway TCP proxy for the import, remove that public route afterward.
Use a fresh demo database and the dedicated account because the import refreshes seeded prices and photos.

## Verify the deployment

1. Open the public site and verify `/healthz` and `/api/v1/actuator/health` both return 200.
2. Search Pune for two future nights, select Gym, and open a hotel card with a photo.
3. Ask the assistant for the same stay and verify it carries Gym into the results.
4. Sign in as the site admin, turn AI off, verify the public status reports unavailable, then turn it back on.
5. Complete a Stripe test booking and verify the webhook confirms it. Keep the public demo in test mode.
