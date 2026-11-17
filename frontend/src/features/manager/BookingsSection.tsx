import { formatDate, formatPrice, pluralize } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';

import { type HotelBooking, useHotelBookings } from './useHotelActivity';

const statusBadge = {
  RESERVED: { label: 'Room held', tone: 'brand' },
  GUESTS_ADDED: { label: 'Guests added', tone: 'brand' },
  PAYMENTS_PENDING: { label: 'Payment pending', tone: 'warning' },
  CONFIRMED: { label: 'Confirmed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
  EXPIRED: { label: 'Expired', tone: 'neutral' },
} as const satisfies Record<HotelBooking['bookingStatus'], { label: string; tone: string }>;

/** Every booking made at this hotel, with its dates, room and where it stands. */
export function BookingsSection({ hotelId }: { hotelId: number }) {
  const bookings = useHotelBookings(hotelId);

  return (
    <section aria-labelledby="bookings-heading" className="flex flex-col gap-4">
      <h2 id="bookings-heading" className="text-xl font-semibold text-ink">
        Bookings
      </h2>

      {bookings.isPending ? (
        <div role="status" aria-label="Loading bookings">
          <Skeleton className="h-24" />
        </div>
      ) : bookings.isError ? (
        <EmptyState title="We could not load the bookings" description={bookings.error.message}>
          <Button
            onClick={() => {
              void bookings.refetch();
            }}
          >
            Try again
          </Button>
        </EmptyState>
      ) : bookings.data.length === 0 ? (
        <EmptyState
          title="No bookings yet"
          description="Bookings appear here as guests hold and pay for rooms."
        />
      ) : (
        <div className="overflow-x-auto rounded-card border border-line bg-white">
          <table className="w-full text-left text-sm">
            <caption className="sr-only">Bookings at this hotel</caption>
            <thead className="border-b border-line text-ink-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">
                  Booking
                </th>
                <th scope="col" className="px-4 py-3 font-medium">
                  Room
                </th>
                <th scope="col" className="px-4 py-3 font-medium">
                  Stay
                </th>
                <th scope="col" className="px-4 py-3 font-medium">
                  Status
                </th>
                <th scope="col" className="px-4 py-3 text-right font-medium">
                  Total
                </th>
              </tr>
            </thead>
            <tbody>
              {bookings.data.map((booking) => {
                const badge = statusBadge[booking.bookingStatus];
                return (
                  <tr key={booking.id} className="border-b border-line last:border-0">
                    <td className="px-4 py-3 text-ink">#{booking.id}</td>
                    <td className="px-4 py-3 text-ink">
                      {booking.roomType} · {pluralize(booking.roomsCount, 'room')}
                    </td>
                    <td className="px-4 py-3 text-ink-soft">
                      {formatDate(booking.checkInDate)} to {formatDate(booking.checkOutDate)}
                    </td>
                    <td className="px-4 py-3">
                      <Badge tone={badge.tone}>{badge.label}</Badge>
                    </td>
                    <td className="px-4 py-3 text-right text-ink">{formatPrice(booking.amount)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
