import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

export type Room = components['schemas']['RoomDto'];
/** A stored room type, which has an id. (The generated type leaves `id` optional: it is read-only.) */
export type SavedRoom = Room & { id: number };

export const roomsQueryKey = (hotelId: number) => ['managed-rooms', hotelId] as const;

function isSaved(room: Room): room is SavedRoom {
  return room.id !== undefined;
}

export function useRooms(hotelId: number) {
  return useQuery({
    queryKey: roomsQueryKey(hotelId),
    queryFn: async (): Promise<SavedRoom[]> => {
      const { data, error, response } = await api.GET('/admin/hotels/{hotelId}/rooms', {
        params: { path: { hotelId } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data.filter(isSaved);
    },
  });
}

/** Adds a room type (no id) or changes one (with its id). */
export function useSaveRoom(hotelId: number, roomId?: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (room: Room): Promise<Room> => {
      const result =
        roomId === undefined
          ? await api.POST('/admin/hotels/{hotelId}/rooms', { params: { path: { hotelId } }, body: room })
          : await api.PUT('/admin/hotels/{hotelId}/rooms/{roomId}', {
              params: { path: { hotelId, roomId } },
              body: room,
            });
      if (!result.data) {
        throw toRequestError(result.response, result.error);
      }
      return result.data;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: roomsQueryKey(hotelId) });
    },
  });
}

export function useDeleteRoom(hotelId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (roomId: number) => {
      const { error, response } = await api.DELETE('/admin/hotels/{hotelId}/rooms/{roomId}', {
        params: { path: { hotelId, roomId } },
      });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: roomsQueryKey(hotelId) });
    },
  });
}
