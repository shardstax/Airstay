const MAX_URL_LENGTH = 2048;

/**
 * True for an https address with a host, at most 2048 characters: the same rule the backend applies to photo links, so
 * the form refuses what the server would. http, javascript:, data: and file: addresses are all refused.
 */
export function isHttpsUrl(value: string): boolean {
  if (value.length === 0 || value.length > MAX_URL_LENGTH) {
    return false;
  }
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname !== '';
  } catch {
    return false;
  }
}
