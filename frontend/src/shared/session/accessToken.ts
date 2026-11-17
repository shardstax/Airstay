/**
 * The short-lived access token lives only in memory: a script on the page can still use it while the page is open, but it is
 * never written to localStorage, where it would outlive the tab and be readable by any injected script. After a reload the
 * session is restored from the httpOnly refresh cookie instead (see refresh.ts).
 */
let accessToken: string | null = null;

// The only thing remembered across reloads is that a session was started, never the token itself. It lets a visitor who
// never signed in skip the refresh request on every page load (it could only answer 401). It is a hint, not a credential:
// forging it only makes the app try a refresh that the server will refuse.
export const SESSION_HINT_KEY = 'airstay.session';

function writeSessionHint(present: boolean): void {
  try {
    if (present) {
      localStorage.setItem(SESSION_HINT_KEY, '1');
    } else {
      localStorage.removeItem(SESSION_HINT_KEY);
    }
  } catch {
    // Storage can be unavailable (private browsing, blocked): the hint is only an optimisation.
  }
}

/** Whether this browser may have a session worth restoring. Errs towards "yes" when storage cannot be read. */
export function hasSessionHint(): boolean {
  try {
    return localStorage.getItem(SESSION_HINT_KEY) === '1';
  } catch {
    return true;
  }
}
const expiredListeners = new Set<() => void>();

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
  writeSessionHint(token !== null);
}

/** Called when the session can no longer be renewed (refresh cookie expired or revoked). Returns an unsubscribe function. */
export function onSessionExpired(listener: () => void): () => void {
  expiredListeners.add(listener);
  return () => expiredListeners.delete(listener);
}

export function notifySessionExpired(): void {
  setAccessToken(null);
  expiredListeners.forEach((listener) => {
    listener();
  });
}
