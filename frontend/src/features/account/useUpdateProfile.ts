import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import { sessionQueryKey } from '@/shared/session/session';

import type { ProfileValues } from './profileSchema';
import { toProfileUpdate } from './profileSchema';

/** Saves the user's own details, then reloads them so the header and this page show the new values. */
export function useUpdateProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (values: ProfileValues) => {
      const { error, response } = await api.PATCH('/users/profile', { body: toProfileUpdate(values) });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: sessionQueryKey });
    },
  });
}
