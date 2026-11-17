import { ApiRequestError } from '@/shared/api/errors';

import { describeAuthError } from './messages';

describe('describeAuthError', () => {
  it('replaces the server wording for a wrong password with something clearer', () => {
    expect(describeAuthError(new ApiRequestError(401, 'Bad credentials'), 'login')).toBe(
      'The email or password is incorrect.',
    );
  });

  it('keeps the server wording for a duplicate email', () => {
    expect(
      describeAuthError(new ApiRequestError(409, 'An account with this email already exists'), 'signup'),
    ).toBe('An account with this email already exists');
  });

  it('explains rate limiting', () => {
    expect(describeAuthError(new ApiRequestError(429, 'Too many requests'), 'login')).toMatch(
      /too many attempts/i,
    );
  });

  it('keeps validation details from the server', () => {
    expect(
      describeAuthError(new ApiRequestError(400, 'Validation failed: email: must be valid'), 'signup'),
    ).toBe('Validation failed: email: must be valid');
  });

  it('says the server is unreachable for a network failure', () => {
    expect(describeAuthError(new TypeError('Failed to fetch'), 'login')).toMatch(
      /could not reach the server/i,
    );
  });
});
