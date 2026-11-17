import { useMutation } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import { navigation } from '@/shared/lib/navigation';

/**
 * Starts (or resumes) the payment and sends the user to the payment page. The server reuses an open payment session, so a
 * second click or a page refresh never creates a second charge.
 */
export function useStartPayment(bookingId: number) {
  return useMutation({
    mutationFn: async (): Promise<string> => {
      const { data, error, response } = await api.POST('/bookings/{bookingId}/payments', {
        params: { path: { bookingId } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data.sessionUrl;
    },
    onSuccess: (sessionUrl) => {
      navigation.redirect(sessionUrl);
    },
  });
}
