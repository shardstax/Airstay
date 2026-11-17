import { z } from 'zod';

import { checkStayDates, parseStay, stayShape, stayToParams } from '@/shared/lib/stay';

export const PAGE_SIZE = 10;

/** What the search form collects, with the same rules the backend applies. */
export function searchSchema(now = new Date()) {
  return z
    .object({
      city: z.string().trim().min(1, 'Enter a city.').max(255, 'Use at most 255 characters.'),
      ...stayShape(now),
    })
    .superRefine(checkStayDates);
}

export type SearchValues = z.infer<ReturnType<typeof searchSchema>>;

/** A search as it lives in the URL: the form values plus the results page (1-based). */
export interface SearchQuery extends SearchValues {
  page: number;
}

export function toSearchParams({
  city,
  page = 1,
  ...stay
}: SearchValues & { page?: number }): URLSearchParams {
  const params = stayToParams(stay);
  params.set('city', city.trim());
  if (page > 1) {
    params.set('page', String(page));
  }
  return params;
}

/** Reads a search back from the URL; null when it is incomplete or invalid, so the page can ask the user to search again. */
export function parseSearchParams(params: URLSearchParams, now = new Date()): SearchQuery | null {
  const stay = parseStay(params, now);
  const city = params.get('city')?.trim();
  if (!stay || !city) {
    return null;
  }
  const page = Number(params.get('page') ?? 1);
  return { city, ...stay, page: Number.isInteger(page) && page >= 1 ? page : 1 };
}
