import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

import { PAGE_SIZE, type SearchQuery } from './searchQuery';

export type HotelSummary = components['schemas']['HotelPriceResponseDto'];

export interface HotelSearchResult {
  hotels: HotelSummary[];
  totalElements: number;
  totalPages: number;
}

/** Hotels with rooms free on every night of the stay, cheapest-room price first shown. The page is 1-based here, 0-based in the API. */
export function useHotelSearch(query: SearchQuery) {
  return useQuery({
    queryKey: ['hotel-search', query],
    queryFn: async (): Promise<HotelSearchResult> => {
      const { data, error, response } = await api.GET('/hotels/search', {
        params: {
          query: {
            city: query.city,
            startDate: query.startDate,
            endDate: query.endDate,
            roomsCount: String(query.rooms),
            page: String(query.page - 1),
            size: String(PAGE_SIZE),
            amenities: query.amenities,
          },
        },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return {
        hotels: data.content ?? [],
        totalElements: data.page?.totalElements ?? 0,
        totalPages: data.page?.totalPages ?? 0,
      };
    },
    // Keep showing the previous page while the next one loads, instead of flashing an empty list.
    placeholderData: keepPreviousData,
  });
}
