/**
 * The page to return to after signing in, taken from router state. Only a path inside this app is accepted: anything
 * else (another site, a protocol-relative `//host`, a backslash trick) falls back, so a crafted link cannot bounce
 * a user to a look-alike site right after they enter their password.
 */
export function safeRedirectPath(value: unknown, fallback = '/'): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return fallback;
  }
  return value;
}
