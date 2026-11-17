import { mockApi } from '@/test/mockApi';

import { getAccessToken, hasSessionHint, setAccessToken } from './accessToken';
import { refreshAccessToken } from './refresh';

describe('refreshAccessToken', () => {
  it('stores and returns the new token', async () => {
    const api = mockApi({ 'POST /auth/refresh': { data: { accessToken: 'fresh' } } });

    expect(await refreshAccessToken()).toBe('fresh');
    expect(getAccessToken()).toBe('fresh');
    expect(api.count('POST /auth/refresh')).toBe(1);
  });

  it('shares one request between callers that ask at the same time', async () => {
    const api = mockApi({ 'POST /auth/refresh': { data: { accessToken: 'fresh' } } });

    const results = await Promise.all([refreshAccessToken(), refreshAccessToken(), refreshAccessToken()]);

    expect(results).toEqual(['fresh', 'fresh', 'fresh']);
    expect(api.count('POST /auth/refresh')).toBe(1);
  });

  it('asks again later, because a finished refresh is not cached', async () => {
    const api = mockApi({ 'POST /auth/refresh': { data: { accessToken: 'fresh' } } });

    await refreshAccessToken();
    await refreshAccessToken();

    expect(api.count('POST /auth/refresh')).toBe(2);
  });

  it('returns null and clears the token when there is no valid session', async () => {
    mockApi({ 'POST /auth/refresh': { status: 401, error: { message: 'Refresh token cookie is missing' } } });

    expect(await refreshAccessToken()).toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it('forgets the remembered session when the server says there is none', async () => {
    setAccessToken('old');
    mockApi({ 'POST /auth/refresh': { status: 401, error: { message: 'Refresh token expired' } } });

    await refreshAccessToken();

    expect(hasSessionHint()).toBe(false);
  });

  it('keeps the remembered session through a server error, which says nothing about it', async () => {
    setAccessToken('old');
    mockApi({ 'POST /auth/refresh': { status: 503, error: { message: 'Unavailable' } } });

    expect(await refreshAccessToken()).toBeNull();
    expect(hasSessionHint()).toBe(true);
  });

  it('returns null without throwing when the network is down', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    expect(await refreshAccessToken()).toBeNull();
  });

  it('treats a success without a token as no session', async () => {
    mockApi({ 'POST /auth/refresh': { data: {} } });

    expect(await refreshAccessToken()).toBeNull();
  });
});
