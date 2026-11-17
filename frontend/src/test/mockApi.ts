import { SESSION_HINT_KEY } from '@/shared/session/accessToken';

export interface Reply {
  status?: number;
  data?: unknown;
  /** A body sent as-is, without the envelope (the actuator health endpoint is not wrapped). */
  raw?: unknown;
  error?: { message: string; subErrors?: string[] };
  headers?: Record<string, string>;
}

type Handler = Reply | ((request: Request) => Reply | Promise<Reply>);

export interface RecordedCall {
  /** "POST /auth/login": the method and the path below /api/v1. */
  key: string;
  authorization: string | null;
  body: unknown;
  /** The query string, e.g. `city=Pune&page=0`. */
  query: URLSearchParams;
}

/**
 * Replaces `fetch` with a fake AirStay backend. Replies are written the way the backend sends them (a `data` envelope for
 * success, an `error` body for failures). A request nobody asked for throws, so a test cannot pass by accident while the
 * code under test calls something unexpected.
 */
export function mockApi(routes: Record<string, Handler>) {
  const calls: RecordedCall[] = [];

  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const request =
      input instanceof Request ? input : new Request(new URL(String(input), window.location.origin), init);
    const key = `${request.method} ${new URL(request.url).pathname.replace('/api/v1', '')}`;

    const text = await request.clone().text();
    calls.push({
      key,
      authorization: request.headers.get('authorization'),
      query: new URL(request.url).searchParams,
      body: text ? (JSON.parse(text) as unknown) : undefined,
    });

    const handler = routes[key];
    if (!handler) {
      throw new Error(`Unexpected request in test: ${key}`);
    }
    const reply = typeof handler === 'function' ? await handler(request) : handler;

    const status = reply.status ?? 200;
    if (reply.raw !== undefined) {
      return new Response(JSON.stringify(reply.raw), {
        status,
        headers: { 'content-type': 'application/json' },
      });
    }
    if (status === 204) {
      return new Response(null, { status });
    }
    const timeStamp = '2026-11-17T10:00:00';
    const body =
      status >= 400
        ? { timeStamp, error: { status: 'ERROR', ...reply.error } }
        : { timeStamp, data: reply.data };
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'content-type': 'application/json', ...reply.headers },
    });
  });

  return {
    calls,
    /** How many times a request like "POST /auth/refresh" was made. */
    count: (key: string) => calls.filter((call) => call.key === key).length,
    keys: () => calls.map((call) => call.key),
  };
}

export const ada = {
  id: 1,
  name: 'Ada Lovelace',
  email: 'ada@example.com',
  roles: ['GUEST'],
};

/** Pretends an earlier visit signed in, so the app tries to restore that session from the refresh cookie. */
export function rememberPreviousSession(): void {
  localStorage.setItem(SESSION_HINT_KEY, '1');
}
