import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

export type Guest = components['schemas']['GuestDto'];
/** A saved guest that has been stored, and so has an id. (The generated type leaves `id` optional: it is read-only.) */
export type SavedGuest = Guest & { id: number };

export const guestsQueryKey = ['guests'] as const;

function isSaved(guest: Guest): guest is SavedGuest {
  return guest.id !== undefined;
}

/** The people the signed-in user has saved, to pick from when booking. */
export function useGuests() {
  return useQuery({
    queryKey: guestsQueryKey,
    queryFn: async (): Promise<SavedGuest[]> => {
      const { data, error, response } = await api.GET('/users/guests');
      if (!data) {
        throw toRequestError(response, error);
      }
      return data.filter(isSaved);
    },
  });
}

export function useCreateGuest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (guest: Guest): Promise<SavedGuest> => {
      const { data, error, response } = await api.POST('/users/guests', { body: guest });
      if (!data || !isSaved(data)) {
        throw toRequestError(response, error);
      }
      return data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: guestsQueryKey });
    },
  });
}

export function useUpdateGuest(guestId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    // The server answers 204 with no body: success is the status, there is no updated guest to read back.
    mutationFn: async (guest: Guest): Promise<void> => {
      const { error, response } = await api.PUT('/users/guests/{guestId}', {
        params: { path: { guestId } },
        body: guest,
      });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: guestsQueryKey });
    },
  });
}

export function useDeleteGuest() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (guestId: number): Promise<void> => {
      const { error, response } = await api.DELETE('/users/guests/{guestId}', {
        params: { path: { guestId } },
      });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: guestsQueryKey });
    },
  });
}
