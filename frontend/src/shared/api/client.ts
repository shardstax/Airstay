import createClient, { type Middleware } from 'openapi-fetch';

import type { paths } from './schema';

interface Envelope {
  timeStamp: unknown;
  data: unknown;
}

/** The backend wraps every successful body as `{ timeStamp, data }`; both keys together identify the wrapper. */
function isEnvelope(body: unknown): body is Envelope {
  return typeof body === 'object' && body !== null && 'timeStamp' in body && 'data' in body;
}

/**
 * The OpenAPI spec (and so the generated types) describe the bare body, but the backend wraps it as
 * `{ timeStamp, data, error }`. This turns a successful wrapped response back into the bare body so the runtime matches
 * the types. Error responses are left as they are: they carry `{ error: { status, message, subErrors } }`.
 */
export async function unwrapEnvelope(response: Response): Promise<Response> {
  const isJson = response.headers.get('content-type')?.includes('application/json') ?? false;
  if (!response.ok || !isJson || response.status === 204) {
    return response;
  }

  const body: unknown = await response.clone().json();
  if (!isEnvelope(body)) {
    return response;
  }

  // The body is re-serialised, so the length and encoding the server reported no longer describe it.
  const headers = new Headers(response.headers);
  headers.delete('content-length');
  headers.delete('content-encoding');
  return new Response(JSON.stringify(body.data), {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

const unwrap: Middleware = {
  async onResponse({ response }) {
    return unwrapEnvelope(response);
  },
};

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

export const api = createClient<paths>({ baseUrl: '/api/v1' });
api.use(unwrap);
