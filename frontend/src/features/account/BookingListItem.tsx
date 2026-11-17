import { useState } from 'react';

import { describeRequestError } from '@/shared/api/errors';
import { formatDate, formatPrice, pluralize } from '@/shared/lib/format';
import { nightsBetween, today } from '@/shared/lib/stay';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { LinkButton } from '@/shared/ui/LinkButton';

import { type MyBooking, useCancelBooking } from './useMyBookings';

const statusBadge = {
  RESERVED: { label: 'Room held', tone: 'brand' },
  GUESTS_ADDED: { label: 'Guests added', tone: 'brand' },
  PAYMENTS_PENDING: { label: 'Payment pending', tone: 'warning' },
  CONFIRMED: { label: 'Confirmed', tone: 'success' },
  CANCELLED: { label: 'Cancelled', tone: 'danger' },
  EXPIRED: { label: 'Expired', tone: 'neutral' },
} as const satisfies Record<MyBooking['bookingStatus'], { label: string; tone: string }>;

const HOLDS: readonly MyBooking['bookingStatus'][] = ['RESERVED', 'GUESTS_ADDED', 'PAYMENTS_PENDING'];

/** One booking in the list, with what the guest can do about it: carry on, look, or cancel with a refund. */
interface BookingListItemProps {
  booking: MyBooking;
  /** Told when the booking was cancelled. The list reports it, because this item moves to another group and is replaced. */
  onCancelled: (booking: MyBooking) => void;
}

export function BookingListItem({ booking, onCancelled }: BookingListItemProps) {
  const cancel = useCancelBooking();
  const [confirming, setConfirming] = useState(false);

  const badge = statusBadge[booking.bookingStatus];
  const isHold = HOLDS.includes(booking.bookingStatus);
  const canCancel = booking.bookingStatus === 'CONFIRMED' && booking.checkInDate > today();
  const nights = nightsBetween(booking.checkInDate, booking.checkOutDate);

  return (
    <Card className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h3 className="text-lg font-semibold text-ink">{booking.hotelName}</h3>
          <p className="text-sm text-ink-soft">
            {booking.roomType} · {pluralize(booking.roomsCount, 'room')} · {pluralize(nights, 'night')}
          </p>
          <p className="mt-1 text-sm text-ink-muted">
            {formatDate(booking.checkInDate)} to {formatDate(booking.checkOutDate)}
          </p>
        </div>
        <div className="shrink-0 text-right">
          <Badge tone={badge.tone}>{badge.label}</Badge>
          <p className="mt-2 font-semibold text-ink">{formatPrice(booking.amount)}</p>
        </div>
      </div>

      {confirming ? (
        <div
          role="group"
          aria-label="Confirm cancellation"
          className="rounded-lg border border-line bg-canvas p-4"
        >
          <p className="text-sm text-ink">
            Cancel this booking? The rooms go back on sale and {formatPrice(booking.amount)} is refunded to
            your original payment method.
          </p>
          {cancel.isError ? (
            <p role="alert" className="mt-2 text-sm text-danger">
              {describeRequestError(cancel.error)}
            </p>
          ) : null}
          <div className="mt-3 flex gap-3">
            <Button
              variant="danger"
              loading={cancel.isPending}
              onClick={() => {
                cancel.mutate(booking.id, {
                  onSuccess: () => {
                    setConfirming(false);
                    onCancelled(booking);
                  },
                });
              }}
            >
              Yes, cancel booking
            </Button>
            <Button
              variant="secondary"
              onClick={() => {
                setConfirming(false);
              }}
            >
              Keep booking
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-3">
          {isHold ? <LinkButton to={`/bookings/${String(booking.id)}`}>Continue booking</LinkButton> : null}
          <LinkButton to={`/bookings/${String(booking.id)}`} variant="secondary">
            View details
          </LinkButton>
          {canCancel ? (
            <Button
              variant="ghost"
              onClick={() => {
                setConfirming(true);
              }}
            >
              Cancel booking
            </Button>
          ) : null}
        </div>
      )}
    </Card>
  );
}
