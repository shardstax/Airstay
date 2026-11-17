import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

export type ManagedHotel = components['schemas']['HotelDto'];

export const myHotelsQueryKey = ['managed-hotels'] as const;
export const hotelQueryKey = (hotelId: number) => ['managed-hotel', hotelId] as const;

/** The hotels the signed-in manager owns, live or not. */
export function useMyHotels() {
  return useQuery({
    queryKey: myHotelsQueryKey,
    queryFn: async (): Promise<ManagedHotel[]> => {
      const { data, error, response } = await api.GET('/admin/hotels');
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
  });
}

export function useHotel(hotelId: number | undefined) {
  return useQuery({
    queryKey: hotelQueryKey(hotelId ?? 0),
    enabled: hotelId !== undefined,
    queryFn: async (): Promise<ManagedHotel> => {
      const { data, error, response } = await api.GET('/admin/hotels/{hotelId}', {
        params: { path: { hotelId: hotelId ?? 0 } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
  });
}

/** Creates a hotel (no id) or updates one (with its id). A new hotel starts as a draft; it goes live with `useActivateHotel`. */
export function useSaveHotel(hotelId?: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (hotel: ManagedHotel): Promise<ManagedHotel> => {
      const result =
        hotelId === undefined
          ? await api.POST('/admin/hotels', { body: hotel })
          : await api.PUT('/admin/hotels/{hotelId}', { params: { path: { hotelId } }, body: hotel });
      if (!result.data) {
        throw toRequestError(result.response, result.error);
      }
      return result.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: myHotelsQueryKey });
      if (hotelId !== undefined) {
        await queryClient.invalidateQueries({ queryKey: hotelQueryKey(hotelId) });
      }
    },
  });
}

/** Puts a hotel on sale: the server creates a year of nightly inventory for its rooms. */
export function useActivateHotel(hotelId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const { error, response } = await api.PATCH('/admin/hotels/{hotelId}/activate', {
        params: { path: { hotelId } },
      });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: myHotelsQueryKey });
      await queryClient.invalidateQueries({ queryKey: hotelQueryKey(hotelId) });
    },
  });
}
