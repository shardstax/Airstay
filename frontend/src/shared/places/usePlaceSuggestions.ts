import { useQuery } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue';

export type Place = components['schemas']['PlaceDto'];

export const MIN_QUERY_LENGTH = 2;
export const SUGGESTION_DELAY_MS = 250;

/**
 * Real places matching what the user has typed. The search waits for a pause in typing and starts at two characters. The
 * server asks a geocoding provider and caches the answers, so repeating a search is cheap.
 */
export function usePlaceSuggestions(text: string) {
  const query = useDebouncedValue(text.trim(), SUGGESTION_DELAY_MS);
  const enabled = query.length >= MIN_QUERY_LENGTH;

  const result = useQuery({
    queryKey: ['places', query],
    enabled,
    staleTime: 5 * 60_000,
    queryFn: async (): Promise<Place[]> => {
      const { data, error, response } = await api.GET('/places', { params: { query: { query } } });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
  });

  return {
    places: enabled ? (result.data ?? []) : [],
    // Typing again right after a pause counts as searching too, so the list never claims "no results" too early.
    isSearching: text.trim() !== query || (enabled && result.isFetching),
    isError: enabled && result.isError,
    isTooShort: text.trim().length < MIN_QUERY_LENGTH,
  };
}
