import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';

import type { Booking } from './bookingStatus';
import { bookingQueryKey } from './useBooking';

/** Names the guests on a booking. Allowed once, while the room is only held; the server checks the guest limit. */
export function useAddGuests(bookingId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (guestIds: number[]): Promise<Booking> => {
      const { data, error, response } = await api.POST('/bookings/{bookingId}/addGuests', {
        params: { path: { bookingId } },
        body: guestIds,
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
    onSuccess: (booking) => {
      queryClient.setQueryData(bookingQueryKey(bookingId), booking);
    },
  });
}
