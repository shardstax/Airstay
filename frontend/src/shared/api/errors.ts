/** The error body the backend sends (see ApiError on the server). */
export interface ApiErrorBody {
  error?: { status?: string; message?: string; subErrors?: string[] };
}

/** A readable message for any failed call, with the field problems when the server listed them. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const detail = (error as ApiErrorBody | undefined)?.error;
  if (!detail?.message) {
    return fallback;
  }
  return detail.subErrors?.length ? `${detail.message}: ${detail.subErrors.join('; ')}` : detail.message;
}

/** A failed API call, carrying the HTTP status so screens can react to it (401, 409, 429, ...). */
export class ApiRequestError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

/** Turns an error from `openapi-fetch` into an `ApiRequestError`, keeping the server's wording. */
export function toRequestError(response: Response, error: unknown): ApiRequestError {
  return new ApiRequestError(response.status, errorMessage(error));
}

/**
 * Wording for any failed request: a network failure (no answer at all) and rate limiting get their own plain explanation;
 * anything else keeps the server's own message, which is already written for people.
 */
export function describeRequestError(error: unknown): string {
  if (!(error instanceof ApiRequestError)) {
    return 'We could not reach the server. Check your connection and try again.';
  }
  if (error.status === 429) {
    return 'Too many attempts. Please wait a minute and try again.';
  }
  return error.message;
}
