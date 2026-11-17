import type { components } from '@/shared/api/schema';

export type Booking = components['schemas']['BookingDto'];
export type BookingStatus = Booking['bookingStatus'];

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'brand';

export const statusBadge: Record<BookingStatus, { label: string; tone: Tone }> = {
  RESERVED: { label: 'Room held', tone: 'brand' },
  GUESTS_ADDED: { label: 'Guests added', tone: 'brand' },
  PAYMENTS_PENDING: { label: 'Payment pending', tone: 'warning' },
  CONFIRMED: { label: 'Confirmed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
  EXPIRED: { label: 'Expired', tone: 'neutral' },
};

const HOLD_STATUSES: readonly BookingStatus[] = ['RESERVED', 'GUESTS_ADDED', 'PAYMENTS_PENDING'];

/** True while rooms are held for the guest but not yet paid for, so the hold clock is running. */
export function isHold(status: BookingStatus): boolean {
  return HOLD_STATUSES.includes(status);
}
