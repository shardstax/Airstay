import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

export type HotelBooking = components['schemas']['BookingDto'];
export type HotelReport = components['schemas']['HotelReportDto'];
export type NightlyInventory = components['schemas']['InventoryDto'];
export type InventoryChange = components['schemas']['UpdateInventoryRequestDto'];

export const inventoryQueryKey = (roomId: number) => ['managed-inventory', roomId] as const;

/** Every booking made at one of the manager's hotels, newest first. */
export function useHotelBookings(hotelId: number) {
  return useQuery({
    queryKey: ['managed-bookings', hotelId],
    queryFn: async (): Promise<HotelBooking[]> => {
      const { data, error, response } = await api.GET('/admin/hotels/{hotelId}/bookings', {
        params: { path: { hotelId } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return [...data].sort((a, b) => b.id - a.id);
    },
  });
}

/** Confirmed bookings and revenue between two dates. */
export function useHotelReport(hotelId: number, startDate: string, endDate: string) {
  return useQuery({
    queryKey: ['managed-report', hotelId, startDate, endDate],
    queryFn: async (): Promise<HotelReport> => {
      const { data, error, response } = await api.GET('/admin/hotels/{hotelId}/reports', {
        params: { path: { hotelId }, query: { startDate, endDate } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
  });
}

/** The nightly prices and room counts of one room type, in date order. */
export function useInventory(roomId: number) {
  return useQuery({
    queryKey: inventoryQueryKey(roomId),
    queryFn: async (): Promise<NightlyInventory[]> => {
      const { data, error, response } = await api.GET('/admin/inventory/rooms/{roomId}', {
        params: { path: { roomId } },
      });
      if (!data) {
        throw toRequestError(response, error);
      }
      return [...data].sort((a, b) => a.date.localeCompare(b.date));
    },
  });
}

/** Applies a price factor and/or closes a range of nights. */
export function useUpdateInventory(roomId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (change: InventoryChange) => {
      const { error, response } = await api.PATCH('/admin/inventory/rooms/{roomId}', {
        params: { path: { roomId } },
        body: change,
      });
      if (!response.ok) {
        throw toRequestError(response, error);
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: inventoryQueryKey(roomId) });
    },
  });
}
