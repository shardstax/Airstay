import createClient, { type Middleware } from 'openapi-fetch';
import type { paths } from './schema';

/**
 * Every AirStay response is wrapped by the backend as `{ timeStamp, data, error }`, but the OpenAPI spec (and so the
 * generated types) describe the bare body. This turns a successful wrapped response back into the bare body so the
 * runtime matches the types. Error responses are left as they are: they carry `{ error: { status, message, subErrors } }`.
 */
export async function unwrapEnvelope(response: Response): Promise<Response> {
  const isJson = response.headers.get('content-type')?.includes('application/json');
  if (!response.ok || !isJson || response.status === 204) return response;

  const body: unknown = await response.clone().json();
  if (typeof body === 'object' && body !== null && 'data' in body) {
    return new Response(JSON.stringify((body as { data: unknown }).data ?? null), {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
    });
  }
  return response;
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
  const e = (error as ApiErrorBody | undefined)?.error;
  if (!e?.message) return fallback;
  return e.subErrors?.length ? `${e.message}: ${e.subErrors.join('; ')}` : e.message;
}

export const api = createClient<paths>({ baseUrl: '/api/v1' });
api.use(unwrap);
