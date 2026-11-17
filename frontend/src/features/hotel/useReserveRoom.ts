import { useMutation } from '@tanstack/react-query';
import { useRef } from 'react';
import { useNavigate } from 'react-router-dom';

import { api } from '@/shared/api/client';
import { ApiRequestError, toRequestError } from '@/shared/api/errors';
import type { Stay } from '@/shared/lib/stay';

export interface ReserveInput {
  hotelId: number;
  roomId: number;
  stay: Stay;
}

/**
 * Holds a room for the stay and opens the booking page. Every attempt carries an Idempotency-Key, so a double click or a retry
 * after a lost response returns the same booking instead of holding a second room. The key is kept only while an answer
 * has not been received; once the server answers (success or refusal) the next attempt is a new one.
 */
export function useReserveRoom() {
  const navigate = useNavigate();
  const attempt = useRef<{ fingerprint: string; key: string } | null>(null);

  return useMutation({
    mutationFn: async ({ hotelId, roomId, stay }: ReserveInput) => {
      const fingerprint = JSON.stringify({ hotelId, roomId, stay });
      if (attempt.current?.fingerprint !== fingerprint) {
        attempt.current = { fingerprint, key: crypto.randomUUID() };
      }
      const { data, error, response } = await api.POST('/bookings/init', {
        params: { header: { 'Idempotency-Key': attempt.current.key } },
        body: {
          hotelId,
          roomId,
          checkInDate: stay.startDate,
          checkOutDate: stay.endDate,
          roomsCount: stay.rooms,
        },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
    onSuccess: (booking) => {
      attempt.current = null;
      void navigate(`/bookings/${String(booking.id)}`);
    },
    onError: (error) => {
      // The server answered (for example "no longer available"): a later click is a fresh attempt. No answer at all
      // (network down) keeps the key, so retrying cannot create a duplicate if the first request actually got through.
      if (error instanceof ApiRequestError) {
        attempt.current = null;
      }
    },
  });
}
