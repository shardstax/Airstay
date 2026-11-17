import { useQuery } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';
import type { Stay } from '@/shared/lib/stay';

export type HotelInfo = components['schemas']['HotelInfoDto'];
export type RoomOffer = components['schemas']['RoomPriceResponseDto'];

/** A hotel and the room types still free on every night of the stay, each with its average price per night. */
export function useHotelInfo(hotelId: number, stay: Stay) {
  return useQuery({
    queryKey: ['hotel-info', hotelId, stay],
    queryFn: async (): Promise<HotelInfo> => {
      const { data, error, response } = await api.GET('/hotels/{hotelId}/info', {
        params: {
          path: { hotelId },
          query: { startDate: stay.startDate, endDate: stay.endDate, roomsCount: String(stay.rooms) },
        },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
  });
}
