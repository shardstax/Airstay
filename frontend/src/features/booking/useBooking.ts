import { useQuery } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';

import type { Booking } from './bookingStatus';

export const bookingQueryKey = (bookingId: number) => ['booking', bookingId] as const;

export const POLL_INTERVAL_MS = 2000;

interface Options {
  /** Keep asking until the payment is no longer pending (used on the page Stripe sends the user back to). */
  pollWhilePaymentPending?: boolean;
}

/** One of the signed-in user's bookings. */
export function useBooking(bookingId: number, { pollWhilePaymentPending = false }: Options = {}) {
  return useQuery({
    queryKey: bookingQueryKey(bookingId),
    queryFn: async (): Promise<Booking> => {
      const { data, error, response } = await api.GET('/bookings/{bookingId}', {
        params: { path: { bookingId } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
    refetchInterval: pollWhilePaymentPending
      ? (query) => (query.state.data?.bookingStatus === 'PAYMENTS_PENDING' ? POLL_INTERVAL_MS : false)
      : false,
  });
}
