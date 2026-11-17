import { ApiRequestError, describeRequestError, errorMessage, toRequestError } from './errors';

describe('errorMessage', () => {
  it('uses the server message', () => {
    expect(errorMessage({ error: { message: 'Room is not available' } })).toBe('Room is not available');
  });

  it('adds the field problems', () => {
    expect(
      errorMessage({
        error: { message: 'Validation failed', subErrors: ['email: must be valid', 'name: required'] },
      }),
    ).toBe('Validation failed: email: must be valid; name: required');
  });

  it('falls back when there is nothing readable', () => {
    expect(errorMessage(undefined)).toMatch(/something went wrong/i);
    expect(errorMessage({}, 'custom')).toBe('custom');
  });
});

describe('toRequestError', () => {
  it('keeps the status and the server wording', () => {
    const error = toRequestError(new Response(null, { status: 409 }), {
      error: { message: 'Email already registered' },
    });

    expect(error).toBeInstanceOf(ApiRequestError);
    expect(error.status).toBe(409);
    expect(error.message).toBe('Email already registered');
  });
});

describe('describeRequestError', () => {
  it('keeps the server wording', () => {
    expect(
      describeRequestError(new ApiRequestError(409, 'Room is not available for the selected dates')),
    ).toBe('Room is not available for the selected dates');
  });

  it('explains rate limiting', () => {
    expect(describeRequestError(new ApiRequestError(429, 'Too many requests'))).toMatch(/too many attempts/i);
  });

  it('says the server is unreachable when there was no answer at all', () => {
    expect(describeRequestError(new TypeError('Failed to fetch'))).toMatch(/could not reach the server/i);
  });
});
