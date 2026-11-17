import { errorMessage, unwrapEnvelope } from './client';

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

describe('unwrapEnvelope', () => {
  it('returns the bare data of a successful wrapped response', async () => {
    const out = await unwrapEnvelope(json({ timeStamp: 'now', data: { id: 7, name: 'Hotel' } }, 201));
    expect(out.status).toBe(201);
    expect(await out.json()).toEqual({ id: 7, name: 'Hotel' });
  });

  it('keeps a paged result intact', async () => {
    const out = await unwrapEnvelope(json({ data: { content: [{ id: 1 }], page: { totalElements: 1 } } }));
    expect(await out.json()).toEqual({ content: [{ id: 1 }], page: { totalElements: 1 } });
  });

  it('leaves error responses alone so the message is still readable', async () => {
    const out = await unwrapEnvelope(json({ error: { status: 'CONFLICT', message: 'Room is not available' } }, 409));
    expect(out.status).toBe(409);
    expect(await out.json()).toEqual({ error: { status: 'CONFLICT', message: 'Room is not available' } });
  });

  it('passes an empty 204 straight through', async () => {
    const response = new Response(null, { status: 204 });
    expect(await unwrapEnvelope(response)).toBe(response);
  });

  it('passes a body without data through unchanged', async () => {
    const out = await unwrapEnvelope(json({ status: 'UP' }));
    expect(await out.json()).toEqual({ status: 'UP' });
  });

  it('turns a null data field into null, not undefined', async () => {
    const out = await unwrapEnvelope(json({ data: null }));
    expect(await out.json()).toBeNull();
  });
});

describe('errorMessage', () => {
  it('uses the server message', () => {
    expect(errorMessage({ error: { message: 'Room is not available' } })).toBe('Room is not available');
  });

  it('adds the field problems', () => {
    expect(errorMessage({ error: { message: 'Validation failed', subErrors: ['email: must be valid', 'name: required'] } }))
      .toBe('Validation failed: email: must be valid; name: required');
  });

  it('falls back when there is nothing readable', () => {
    expect(errorMessage(undefined)).toMatch(/something went wrong/i);
    expect(errorMessage({}, 'custom')).toBe('custom');
  });
});
