# AirStay frontend

React 19 + strict TypeScript + Vite, styled with Tailwind CSS. It talks to the AirStay API through one origin: the browser only
calls `/api`, which Nginx (in Docker) or the Vite dev server forwards to the backend. That keeps the refresh-token cookie
(`SameSite=Strict`, path `/api/v1/auth`) a first-party cookie and needs no CORS.

Nothing needs installing on your machine: everything below runs in Docker. Open the whole product with
`docker compose up --build` and visit <http://localhost:5173>.

## Stack and why

| Tool                                                              | Why                                                                         |
| ----------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Vite + React 19 + TypeScript (strict, `noUncheckedIndexedAccess`) | fast builds, typed end to end                                               |
| TanStack Query                                                    | server data: caching, retries, loading and error states                     |
| `openapi-typescript` + `openapi-fetch`                            | request and response types **generated from the backend's OpenAPI spec**    |
| Tailwind CSS v4, design tokens in `src/index.css`                 | Stripe blue (`#0570de`) as the single accent, deep-navy text, quiet borders |
| Vitest + Testing Library                                          | unit and component tests                                                    |
| ESLint (strict, type-aware) + Prettier                            | enforced standards, see below                                               |
| Self-hosted Inter font                                            | no third-party requests, so the CSP can stay `default-src 'self'`           |

TypeScript is pinned to 5.x: `openapi-typescript` needs the TypeScript 5 compiler API, which TypeScript 7 no longer provides.
ESLint is pinned to 9 because `eslint-plugin-jsx-a11y` does not support 10 yet. Revisit both when those catch up.

## Standards, enforced by tooling (not by good intentions)

`npm run check` runs all of this, CI runs it on every push, and a failure blocks the build:

- **TypeScript strict**, plus `noUncheckedIndexedAccess`, `noUnusedLocals` and `noUnusedParameters`.
- **ESLint** with the type-aware `strict` and `stylistic` rule sets, React and React Hooks rules, and `jsx-a11y` accessibility
  rules. Zero warnings are allowed. Also enforced: named exports only, `import type` for types, no `console.log`, `===` only, braces
  always, no floating promises, exhaustive `switch`, sorted imports.
- **Prettier** formats everything (`npm run format`); ESLint never argues about formatting.
- **Architecture boundaries** (`eslint-plugin-boundaries`): dependencies point one way, `app` to `features` to `shared`, and a
  feature never imports another feature. Importing against the grain is a lint error, so the structure cannot erode silently.
- **Tests** for logic and components; accessibility is part of the component tests (labels, roles, announced errors).

### Conventions

- **Structure by product area**, not by file type: `src/features/<name>/` holds that area's screens, hooks and components. Anything
  used by two features moves to `src/shared/`.
- **One component per file**, named like the file (`Button.tsx` exports `Button`), PascalCase; hooks start with `use`; plain
  modules are camelCase.
- **Server data goes through the typed API client and TanStack Query**, never raw `fetch` in a component (the health check is the
  one exception: that endpoint is not part of the API spec).
- **No `any`, no `as` to silence the compiler.** Unknown data is narrowed with a type guard (see `isEnvelope`).
- **Comments say why, not what.** If a comment restates the code, delete it.
- **Style with the design tokens** (`bg-brand-600`, `text-ink`), not hard-coded colours. See `/design`.
- **Accessibility is not optional**: every field has a label, errors are announced, interactive elements are real buttons and
  links, focus is visible.

## Commands (in Docker)

```bash
# Git Bash on Windows needs MSYS_NO_PATHCONV=1. node_modules lives in a Docker volume, not in your folder.
RUN="docker run --rm -v $PWD/frontend:/app -v airstay_fe_node_modules:/app/node_modules -w /app node:22-alpine"

$RUN npm run check          # typecheck + lint + format check + tests: what CI runs
$RUN npm run lint:fix       # fix what ESLint can (import order, type imports, ...)
$RUN npm run format         # Prettier
$RUN npm run build          # production build into dist/

# Regenerate the API types after a backend change (the stack must be running):
docker run --rm --network airstay_default -v "$PWD/frontend":/app -v airstay_fe_node_modules:/app/node_modules -w /app \
  -e OPENAPI_URL=http://app:8080/api/v1/v3/api-docs node:22-alpine npm run api:generate

# Dev server with hot reload on http://localhost:5174 (talks to the running stack):
docker run --rm -p 5174:5173 --network airstay_default -e VITE_POLLING=1 \
  -v "$PWD/frontend":/app -v airstay_fe_node_modules:/app/node_modules -w /app node:22-alpine npm run dev
```

CI has two jobs for the frontend. The `frontend` job runs typecheck, lint, format check, tests and the build. The `docker` job, which
has the stack running, also fails if `src/shared/api/schema.d.ts` no longer matches the backend's live spec, and smoke-tests the
page and the `/api` proxy.

## How the API is used

Every backend response is wrapped as `{ timeStamp, data, error }`, but the OpenAPI spec describes the bare body.
`src/shared/api/client.ts` unwraps successful responses in one place (`unwrapEnvelope`; both `timeStamp` and `data` must be present,
so a body that merely has a `data` field is left alone), so the types match what the code receives. Errors keep their body;
`errorMessage()` turns one into readable text.

The backend marks the fields it always returns as required in the spec (`@Schema(requiredMode = REQUIRED)`, pinned by a test in
`AirStayApplicationIT`), so the generated types are exact: `price: number`, `photos?: string[]`. The few that stay optional are the read-only
ids shared with request bodies (`HotelDto.id`, `GuestDto.id`) and Spring Data's `PagedModel`, whose fields are all optional.

## Search and the URL

A search and a hotel page keep everything in the URL (`/search?city=Pune&startDate=2026-12-20&endDate=2026-12-22&rooms=1&page=2`,
`/hotels/7?startDate=...`), so links can be shared, the back button works and a reload keeps the search. The URL is read back through the
same validation as the form (`parseStay`, `parseSearchParams`): an incomplete, hand-edited or outdated link asks for a new search instead of
sending a bad request. Dates are plain `YYYY-MM-DD` strings and the arithmetic goes through UTC, so daylight-saving changes cannot move a
day. While the next results page loads, the previous one stays visible (dimmed) instead of flashing empty.

The rules a stay must obey (check-in not in the past, check-out after check-in, at most 30 nights, 1 to 10 rooms) mirror the backend's and
live in `shared/lib/stay.ts`, so the form, the search page and the hotel page cannot disagree.

## Sign-in and the session

- The **access token lives in memory only** (it expires after 10 minutes). It is never written to storage, so an injected script cannot
  find it after the tab closes.
- A **reload restores the session** from the httpOnly refresh cookie (`POST /auth/refresh`). The only thing kept in
  `localStorage` is a plain "a session was started" flag, so visitors who never signed in make no failing refresh request.
  The flag is a hint, not a credential.
- When a call answers 401, the client **refreshes once and replays the request once**; several calls expiring together share one refresh.
  Sign-in, sign-up, refresh and sign-out never trigger a refresh. If the session cannot be renewed the user is sent to sign in again,
  with an explanation, and returns to where they were. The return path is checked so only paths inside the app are accepted.
- Pages that need an account sit under `RequireAuth`; sign-in and sign-up sit under `GuestOnly`. Both live in `src/shared/session`
  because every feature needs them and features may not import each other.

## Running behind the proxy: things to know

- The refresh cookie is `Secure`. Chrome and Firefox accept it on `http://localhost`; Safari, and any other plain-HTTP address such as
  `http://192.168.x.x:5173`, drop it, so login would not persist there. Use `localhost` (or HTTPS) when trying the app.
- Nginx adds the security headers (CSP, `X-Frame-Options`, ...) to the web app only, not to `/api/`. A page-level CSP would break the
  backend's Swagger UI, which is still available directly on port 8080.
- The app container trusts `X-Forwarded-For` from the proxy (`SERVER_FORWARD_HEADERS_STRATEGY=framework` in `docker-compose.yml`) so
  the per-IP rate limits see the real client address.

## Layout

```
src/app/        wiring: App (routes, layout), Header, NotFoundPage
src/features/   one folder per product area: search/ (home, results), hotel/, auth/ (sign in, sign up), account/, design/ (/design)
src/shared/     api/ (generated schema, typed client), session/ (token, refresh, route guards), ui/, lib/ (stay and date rules, formats)
src/test/       test setup
```
