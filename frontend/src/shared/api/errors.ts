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
