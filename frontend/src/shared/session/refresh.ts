import { unwrapEnvelope } from '@/shared/api/envelope';

import { setAccessToken } from './accessToken';

let inFlight: Promise<string | null> | null = null;

function readAccessToken(body: unknown): string | null {
  if (
    typeof body === 'object' &&
    body !== null &&
    'accessToken' in body &&
    typeof body.accessToken === 'string'
  ) {
    return body.accessToken;
  }
  return null;
}

async function requestNewToken(): Promise<string | null> {
  try {
    // Only the httpOnly refresh cookie is sent. Plain fetch, not the API client: the client calls this function.
    const response = await unwrapEnvelope(await fetch('/api/v1/auth/refresh', { method: 'POST' }));
    if (!response.ok) {
      // 401/403 mean the cookie is missing, expired or revoked: there is no session. A server error says nothing about
      // the session, so keep what we have and let the next attempt try again.
      if (response.status === 401 || response.status === 403) {
        setAccessToken(null);
      }
      return null;
    }
    const token = readAccessToken(await response.json());
    setAccessToken(token);
    return token;
  } catch {
    // Network failure: we cannot tell whether the session is valid, so report "no token" without claiming it expired.
    return null;
  }
}

/**
 * Exchanges the refresh cookie for a new access token. Several callers at once (a page loading three requests that all got
 * 401) share one request, because each refresh would otherwise race the others. Resolves to null when there is no valid session.
 */
export function refreshAccessToken(): Promise<string | null> {
  inFlight ??= requestNewToken().finally(() => {
    inFlight = null;
  });
  return inFlight;
}
