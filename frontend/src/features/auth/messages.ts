import { ApiRequestError } from '@/shared/api/errors';

export type AuthAction = 'login' | 'signup';

/** Wording for a failed sign-in or sign-up, keeping the server's text where it is already clear. */
export function describeAuthError(error: unknown, action: AuthAction): string {
  if (!(error instanceof ApiRequestError)) {
    return 'We could not reach the server. Check your connection and try again.';
  }
  if (error.status === 429) {
    return 'Too many attempts. Please wait a minute and try again.';
  }
  if (action === 'login' && error.status === 401) {
    return 'The email or password is incorrect.';
  }
  return error.message;
}
