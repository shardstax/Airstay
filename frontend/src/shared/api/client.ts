import createClient, { type Middleware } from 'openapi-fetch';

import { getAccessToken, notifySessionExpired } from '@/shared/session/accessToken';
import { refreshAccessToken } from '@/shared/session/refresh';

import { unwrapEnvelope } from './envelope';
import type { paths } from './schema';

const API_PREFIX = '/api/v1';

/** Login, refresh, logout and sign-up answer 401 for reasons that a new token cannot fix, so they never trigger a refresh. */
function isAuthEndpoint(url: string): boolean {
  return new URL(url).pathname.startsWith(`${API_PREFIX}/auth/`);
}

const unwrap: Middleware = {
  async onResponse({ response }) {
    return unwrapEnvelope(response);
  },
};

/** Requests that went out with a token, kept by id so one can be replayed if the token turns out to have expired. */
const replayable = new Map<string, Request>();

const authorize: Middleware = {
  onRequest({ request, id }) {
    const token = getAccessToken();
    if (token && !isAuthEndpoint(request.url)) {
      request.headers.set('Authorization', `Bearer ${token}`);
      replayable.set(id, request.clone());
    }
    return request;
  },

  async onResponse({ response, id }) {
    const original = replayable.get(id);
    replayable.delete(id);
    if (response.status !== 401 || !original) {
      return response;
    }

    // The access token expired (it lives 10 minutes). Renew it once and replay the request once; a second 401 means
    // the session is really gone.
    const token = await refreshAccessToken();
    if (!token) {
      notifySessionExpired();
      return response;
    }
    original.headers.set('Authorization', `Bearer ${token}`);
    const retried = await fetch(original);
    if (retried.status === 401) {
      notifySessionExpired();
    }
    return retried;
  },

  onError({ id }) {
    replayable.delete(id);
  },
};

export const api = createClient<paths>({
  // Absolute, built from the page's own origin: browsers accept a relative base, but a Request outside a page does not.
  baseUrl: `${globalThis.location.origin}${API_PREFIX}`,
  // Looked up at call time, so tests (and any future wrapper) can replace the global fetch.
  fetch: (request) => globalThis.fetch(request),
});

// onResponse middleware runs in reverse registration order: `authorize` sees the raw response first (and may replay the
// request), then `unwrap` removes the envelope from whichever response is the final one.
api.use(unwrap);
api.use(authorize);
