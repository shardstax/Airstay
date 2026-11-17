import { formatDate, formatPrice, pluralize } from '@/shared/lib/format';
import { nightsBetween } from '@/shared/lib/stay';
import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';

import { type Booking, statusBadge } from './bookingStatus';

/** What was booked: hotel, room, dates, the total, and where the booking stands. */
export function BookingSummary({ booking }: { booking: Booking }) {
  const nights = nightsBetween(booking.checkInDate, booking.checkOutDate);
  const status = statusBadge[booking.bookingStatus];

  return (
    <Card>
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="text-lg font-semibold text-ink">{booking.hotelName}</h2>
          <p className="text-ink-soft">
            {booking.roomType} · {pluralize(booking.roomsCount, 'room')}
          </p>
        </div>
        <Badge tone={status.tone}>{status.label}</Badge>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-4 border-t border-line pt-4 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-ink-muted">Check-in</dt>
          <dd className="mt-0.5 text-ink">{formatDate(booking.checkInDate)}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Check-out</dt>
          <dd className="mt-0.5 text-ink">{formatDate(booking.checkOutDate)}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Stay</dt>
          <dd className="mt-0.5 text-ink">{pluralize(nights, 'night')}</dd>
        </div>
        <div>
          <dt className="text-ink-muted">Total</dt>
          <dd className="mt-0.5 font-semibold text-ink">{formatPrice(booking.amount)}</dd>
        </div>
      </dl>
    </Card>
  );
}
