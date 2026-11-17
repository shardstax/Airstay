import { unwrapEnvelope } from './envelope';

const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });

const readJson = async (response: Response): Promise<unknown> => response.json();

const wrapped = (data: unknown) => ({ timeStamp: '2026-11-17T10:00:00', data });

describe('unwrapEnvelope', () => {
  it('returns the bare data of a successful wrapped response', async () => {
    const out = await unwrapEnvelope(json(wrapped({ id: 7, name: 'Hotel' }), 201));

    expect(out.status).toBe(201);
    expect(await readJson(out)).toEqual({ id: 7, name: 'Hotel' });
  });

  it('keeps a paged result intact', async () => {
    const page = { content: [{ id: 1 }], page: { totalElements: 1 } };

    expect(await readJson(await unwrapEnvelope(json(wrapped(page))))).toEqual(page);
  });

  it('accepts a content type that carries a charset', async () => {
    const response = json(wrapped({ id: 1 }), 200, { 'content-type': 'application/json;charset=UTF-8' });

    expect(await readJson(await unwrapEnvelope(response))).toEqual({ id: 1 });
  });

  it('turns a null data field into null', async () => {
    expect(await readJson(await unwrapEnvelope(json(wrapped(null))))).toBeNull();
  });

  it('drops the length and encoding headers of the original body', async () => {
    const response = json(wrapped({ id: 1 }), 200, { 'content-length': '999', 'content-encoding': 'gzip' });

    const out = await unwrapEnvelope(response);

    expect(out.headers.get('content-length')).toBeNull();
    expect(out.headers.get('content-encoding')).toBeNull();
    expect(out.headers.get('content-type')).toContain('application/json');
  });

  it('leaves error responses alone so the message is still readable', async () => {
    const body = { error: { status: 'CONFLICT', message: 'Room is not available' } };
    const out = await unwrapEnvelope(json(body, 409));

    expect(out.status).toBe(409);
    expect(await readJson(out)).toEqual(body);
  });

  it('passes an empty 204 straight through', async () => {
    const response = new Response(null, { status: 204 });

    expect(await unwrapEnvelope(response)).toBe(response);
  });

  it('passes a body without the envelope keys through unchanged', async () => {
    expect(await readJson(await unwrapEnvelope(json({ status: 'UP' })))).toEqual({ status: 'UP' });
  });

  it('does not unwrap a body that merely has a data field of its own', async () => {
    const body = { data: [1, 2, 3], label: 'not an envelope' };

    expect(await readJson(await unwrapEnvelope(json(body)))).toEqual(body);
  });

  it('passes a non-JSON success response straight through', async () => {
    const response = new Response('ok', { status: 200, headers: { 'content-type': 'text/plain' } });

    expect(await unwrapEnvelope(response)).toBe(response);
  });
});
