import { ApiRequestError, describeRequestError } from '@/shared/api/errors';

export type AuthAction = 'login' | 'signup';

/** Wording for a failed sign-in or sign-up. A wrong password gets a clearer sentence than the server's "Bad credentials". */
export function describeAuthError(error: unknown, action: AuthAction): string {
  if (action === 'login' && error instanceof ApiRequestError && error.status === 401) {
    return 'The email or password is incorrect.';
  }
  return describeRequestError(error);
}
