import { getAccessToken, onSessionExpired, setAccessToken } from '@/shared/session/accessToken';
import { ada, mockApi } from '@/test/mockApi';

import { api } from './client';

const unauthorized = { status: 401, error: { message: 'Expired or invalid token' } };

describe('the API client and the access token', () => {
  it('sends the access token, and sends nothing when there is none', async () => {
    const backend = mockApi({ 'GET /users/profile': { data: ada } });

    await api.GET('/users/profile');
    setAccessToken('abc');
    await api.GET('/users/profile');

    expect(backend.calls.map((call) => call.authorization)).toEqual([null, 'Bearer abc']);
  });

  it('returns the bare body, not the envelope', async () => {
    mockApi({ 'GET /users/profile': { data: ada } });

    const { data } = await api.GET('/users/profile');

    expect(data).toEqual(ada);
  });

  it('renews an expired token once and replays the request with the new one', async () => {
    setAccessToken('expired');
    const backend = mockApi({
      'GET /users/profile': (request) =>
        request.headers.get('authorization') === 'Bearer fresh' ? { data: ada } : unauthorized,
      'POST /auth/refresh': { data: { accessToken: 'fresh' } },
    });

    const { data } = await api.GET('/users/profile');

    expect(data).toEqual(ada);
    expect(backend.keys()).toEqual(['GET /users/profile', 'POST /auth/refresh', 'GET /users/profile']);
    expect(getAccessToken()).toBe('fresh');
  });

  it('replays a request that has a body', async () => {
    setAccessToken('expired');
    const backend = mockApi({
      'POST /users/guests': (request) =>
        request.headers.get('authorization') === 'Bearer fresh'
          ? { status: 201, data: { id: 5 } }
          : unauthorized,
      'POST /auth/refresh': { data: { accessToken: 'fresh' } },
    });

    const { data } = await api.POST('/users/guests', { body: { name: 'Traveller' } });

    expect(data).toEqual({ id: 5 });
    expect(
      backend.calls.filter((call) => call.key === 'POST /users/guests').map((call) => call.body),
    ).toEqual([{ name: 'Traveller' }, { name: 'Traveller' }]);
  });

  it('refreshes only once when several requests expire together', async () => {
    setAccessToken('expired');
    const backend = mockApi({
      'GET /users/profile': (request) =>
        request.headers.get('authorization') === 'Bearer fresh' ? { data: ada } : unauthorized,
      'GET /users/guests': (request) =>
        request.headers.get('authorization') === 'Bearer fresh' ? { data: [] } : unauthorized,
      'POST /auth/refresh': { data: { accessToken: 'fresh' } },
    });

    const [profile, guests] = await Promise.all([api.GET('/users/profile'), api.GET('/users/guests')]);

    expect(profile.data).toEqual(ada);
    expect(guests.data).toEqual([]);
    expect(backend.count('POST /auth/refresh')).toBe(1);
  });

  it('announces the end of the session when the token cannot be renewed', async () => {
    setAccessToken('expired');
    mockApi({
      'GET /users/profile': unauthorized,
      'POST /auth/refresh': { status: 401, error: { message: 'Refresh token expired' } },
    });
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    const { response } = await api.GET('/users/profile');
    unsubscribe();

    expect(response.status).toBe(401);
    expect(expired).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
  });

  it('gives up after one replay instead of looping', async () => {
    setAccessToken('expired');
    const backend = mockApi({
      'GET /users/profile': unauthorized,
      'POST /auth/refresh': { data: { accessToken: 'fresh' } },
    });
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    await api.GET('/users/profile');
    unsubscribe();

    expect(backend.count('GET /users/profile')).toBe(2);
    expect(backend.count('POST /auth/refresh')).toBe(1);
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it('never refreshes because of a failed login', async () => {
    setAccessToken('some-token');
    const backend = mockApi({ 'POST /auth/login': { status: 401, error: { message: 'Bad credentials' } } });
    const expired = vi.fn();
    const unsubscribe = onSessionExpired(expired);

    const { response } = await api.POST('/auth/login', { body: { email: 'a@b.co', password: 'wrong' } });
    unsubscribe();

    expect(response.status).toBe(401);
    expect(backend.keys()).toEqual(['POST /auth/login']);
    expect(backend.calls[0]?.authorization).toBeNull();
    expect(expired).not.toHaveBeenCalled();
  });

  it('does not refresh for errors other than 401', async () => {
    setAccessToken('valid');
    const backend = mockApi({ 'GET /users/profile': { status: 403, error: { message: 'Forbidden' } } });

    await api.GET('/users/profile');

    expect(backend.keys()).toEqual(['GET /users/profile']);
  });
});
