import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

export type MyBooking = components['schemas']['BookingDto'];

export const myBookingsQueryKey = ['my-bookings'] as const;

/** Every booking the signed-in user has made, newest first. */
export function useMyBookings() {
  return useQuery({
    queryKey: myBookingsQueryKey,
    queryFn: async (): Promise<MyBooking[]> => {
      const { data, error, response } = await api.GET('/users/myBookings');
      if (!data) {
        throw toRequestError(response, error);
      }
      return [...data].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '') || b.id - a.id);
    },
  });
}

/** Cancels a paid booking (before check-in). The refund is started by the server right after. */
export function useCancelBooking() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (bookingId: number) => {
      const { error, response } = await api.POST('/bookings/{bookingId}/cancel', {
        params: { path: { bookingId } },
      });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async (_data, bookingId) => {
      await queryClient.invalidateQueries({ queryKey: myBookingsQueryKey });
      await queryClient.invalidateQueries({ queryKey: ['booking', bookingId] });
    },
  });
}
