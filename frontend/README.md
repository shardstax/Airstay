# AirStay frontend

React 19 + strict TypeScript + Vite, styled with Tailwind CSS. It talks to the AirStay API through one origin: the browser only
calls `/api`, which Nginx (in Docker) or the Vite dev server forwards to the backend. That keeps the refresh-token cookie
(`SameSite=Strict`, path `/api/v1/auth`) a first-party cookie and needs no CORS.

Nothing needs installing on your machine: everything below runs in Docker. Open the whole product with
`docker compose up --build` and visit <http://localhost:5173>.

## Stack and why

| | |
|---|---|
| Vite + React 19 + TypeScript (strict, `noUncheckedIndexedAccess`) | fast builds, typed end to end |
| TanStack Query | server data: caching, retries, loading and error states |
| `openapi-typescript` + `openapi-fetch` | request and response types **generated from the backend's OpenAPI spec**, so a backend change that breaks the UI fails at compile time |
| Tailwind CSS v4 with design tokens in `src/index.css` | one calm blue, deep-navy text, quiet borders (Stripe-docs-like) |
| Vitest + Testing Library | unit and component tests |
| Self-hosted Inter font | no third-party requests, so the CSP can stay `default-src 'self'` |

## Commands (in Docker)

```bash
# Git Bash on Windows needs MSYS_NO_PATHCONV=1. node_modules lives in a Docker volume, not in your folder.
RUN="docker run --rm -v $PWD/frontend:/app -v airstay_fe_node_modules:/app/node_modules -w /app node:22-alpine"

$RUN sh -c "npm ci && npm run typecheck && npm test"       # typecheck and tests
$RUN npm run build                                         # production build into dist/

# Regenerate the API types after a backend change (the stack must be running):
docker run --rm --network airstay_default -v "$PWD/frontend":/app -v airstay_fe_node_modules:/app/node_modules -w /app \
  -e OPENAPI_URL=http://app:8080/api/v1/v3/api-docs node:22-alpine npm run api:generate

# Dev server with hot reload on http://localhost:5174 (talks to the running stack):
docker run --rm -p 5174:5173 --network airstay_default -e VITE_POLLING=1 \
  -v "$PWD/frontend":/app -v airstay_fe_node_modules:/app/node_modules -w /app node:22-alpine npm run dev
```

CI runs the typecheck, tests and build, and fails if `src/api/schema.d.ts` no longer matches the live backend spec.

## How the API is used

Every backend response is wrapped as `{ timeStamp, data, error }`, but the OpenAPI spec describes the bare body. `src/api/client.ts`
unwraps successful responses in one place (`unwrapEnvelope`), so the types match what the code receives. Errors keep their body;
`errorMessage()` turns one into readable text.

Known limitation: the spec does not mark response fields as required, so the generated types make every field optional (`id?: number`).
Pages must handle that, or the backend can add `@Schema(requiredMode = REQUIRED)` to its DTOs.

## Layout

```
src/api/         generated schema.d.ts, the typed client, health check hook
src/components/  ui/ building blocks (Button, Card, Input, Badge)
src/pages/       one file per screen (/design shows the building blocks)
src/lib/         small helpers
```
