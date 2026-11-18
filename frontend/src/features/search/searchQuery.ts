import { z } from 'zod';

import { checkStayDates, parseStay, stayShape, stayToParams } from '@/shared/lib/stay';

export const PAGE_SIZE = 10;

/** What the search form collects, with the same rules the backend applies. */
export function searchSchema(now = new Date()) {
  return z
    .object({
      // The label of a place chosen from the picker ("Pune, Maharashtra, India"), never free text.
      city: z.string().trim().min(1, 'Choose a place from the list.').max(255, 'Use at most 255 characters.'),
      ...stayShape(now),
      amenities: z.array(z.string().trim().min(1)).max(15).optional(),
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
  amenities,
  page = 1,
  ...stay
}: SearchValues & { page?: number }): URLSearchParams {
  const params = stayToParams(stay);
  params.set('city', city.trim());
  for (const amenity of [...new Set(amenities)].sort()) params.append('amenities', amenity);
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
  const amenities = [...new Set(params.getAll('amenities'))].sort();
  if (amenities.length > 15 || amenities.some((amenity) => !amenity.trim())) return null;
  return {
    city,
    ...stay,
    ...(amenities.length ? { amenities } : {}),
    page: Number.isInteger(page) && page >= 1 ? page : 1,
  };
}
