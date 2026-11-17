import { useState } from 'react';

import { formatPrice } from '@/shared/lib/format';
import { today } from '@/shared/lib/stay';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { BookingListItem } from './BookingListItem';
import { type MyBooking, useMyBookings } from './useMyBookings';

const ACTIVE: readonly MyBooking['bookingStatus'][] = [
  'RESERVED',
  'GUESTS_ADDED',
  'PAYMENTS_PENDING',
  'CONFIRMED',
];

/** Bookings still ahead of the guest (held or confirmed and not yet over) versus everything else. */
function splitBookings(bookings: MyBooking[]) {
  const current = today();
  const upcoming = bookings.filter((b) => ACTIVE.includes(b.bookingStatus) && b.checkOutDate >= current);
  const earlier = bookings.filter((b) => !upcoming.includes(b));
  return { upcoming, earlier };
}

export function BookingsPage() {
  const query = useMyBookings();
  // Kept here, not in the item: a cancelled booking moves to "Earlier and closed", so its own item is replaced.
  const [cancelled, setCancelled] = useState<MyBooking | null>(null);

  if (query.isPending) {
    return (
      <div role="status" aria-label="Loading your bookings" className="flex flex-col gap-4">
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <EmptyState title="We could not load your bookings" description={query.error.message}>
        <Button
          onClick={() => {
            void query.refetch();
          }}
        >
          Try again
        </Button>
      </EmptyState>
    );
  }
  if (query.data.length === 0) {
    return (
      <EmptyState title="No bookings yet" description="When you book a room, it will show up here.">
        <LinkButton to="/">Search hotels</LinkButton>
      </EmptyState>
    );
  }

  const { upcoming, earlier } = splitBookings(query.data);
  return (
    <div className="flex flex-col gap-8">
      {cancelled ? (
        <p role="status" className="rounded-lg bg-success-bg px-3 py-2 text-sm text-success">
          Your booking at {cancelled.hotelName} was cancelled. A refund of {formatPrice(cancelled.amount)} has
          been requested.
        </p>
      ) : null}
      <BookingGroup
        title="Upcoming and in progress"
        bookings={upcoming}
        emptyText="Nothing coming up."
        onCancelled={setCancelled}
      />
      <BookingGroup
        title="Earlier and closed"
        bookings={earlier}
        emptyText="Nothing here yet."
        onCancelled={setCancelled}
      />
    </div>
  );
}

function BookingGroup({
  title,
  bookings,
  emptyText,
  onCancelled,
}: {
  title: string;
  bookings: MyBooking[];
  emptyText: string;
  onCancelled: (booking: MyBooking) => void;
}) {
  return (
    <section aria-label={title}>
      <h2 className="mb-3 text-lg font-semibold text-ink">{title}</h2>
      {bookings.length === 0 ? (
        <p className="text-sm text-ink-muted">{emptyText}</p>
      ) : (
        <ul className="flex flex-col gap-4">
          {bookings.map((booking) => (
            <li key={booking.id}>
              <BookingListItem booking={booking} onCancelled={onCancelled} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
