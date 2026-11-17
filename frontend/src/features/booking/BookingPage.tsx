import { useParams } from 'react-router-dom';

import { ApiRequestError } from '@/shared/api/errors';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { type Booking, isHold } from './bookingStatus';
import { BookingSummary } from './BookingSummary';
import { GuestsStep } from './GuestsStep';
import { HoldBanner } from './HoldBanner';
import { PaymentStep } from './PaymentStep';
import { useBooking } from './useBooking';

export function BookingPage() {
  const { bookingId: bookingIdParam } = useParams();
  const bookingId = Number(bookingIdParam);

  if (!Number.isInteger(bookingId) || bookingId < 1) {
    return <NotFound />;
  }
  return <BookingDetails bookingId={bookingId} />;
}

function NotFound() {
  return (
    <EmptyState
      title="Booking not found"
      description="This booking does not exist, or it belongs to another account."
    >
      <LinkButton to="/">Search hotels</LinkButton>
    </EmptyState>
  );
}

function BookingDetails({ bookingId }: { bookingId: number }) {
  const query = useBooking(bookingId);

  if (query.isPending) {
    return (
      <div role="status" aria-label="Loading your booking" className="mx-auto flex max-w-2xl flex-col gap-4">
        <Skeleton className="h-32" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (query.isError) {
    const missing = query.error instanceof ApiRequestError && [403, 404].includes(query.error.status);
    return missing ? (
      <NotFound />
    ) : (
      <EmptyState title="We could not load your booking" description={query.error.message}>
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

  const booking = query.data;
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Complete your booking</h1>
      {isHold(booking.bookingStatus) ? (
        <HoldBanner
          expiresAt={booking.expiresAt}
          onExpired={() => {
            void query.refetch();
          }}
        />
      ) : null}
      <BookingSummary booking={booking} />
      <NextStep booking={booking} />
    </div>
  );
}

/** What the guest does next, chosen by where the booking stands. */
function NextStep({ booking }: { booking: Booking }) {
  switch (booking.bookingStatus) {
    case 'RESERVED':
      return <GuestsStep booking={booking} />;
    case 'GUESTS_ADDED':
    case 'PAYMENTS_PENDING':
      return <PaymentStep booking={booking} />;
    case 'CONFIRMED':
      return (
        <Card>
          <h2 className="text-lg font-semibold text-ink">Your booking is confirmed</h2>
          <p className="mt-1 text-sm text-ink-soft">
            We have sent the details to your email. Enjoy your stay.
          </p>
          <div className="mt-6">
            <LinkButton to="/account" variant="secondary">
              Go to my account
            </LinkButton>
          </div>
        </Card>
      );
    case 'CANCELLED':
    case 'EXPIRED':
      return (
        <EmptyState
          title={booking.bookingStatus === 'EXPIRED' ? 'This hold has ended' : 'This booking was cancelled'}
          description={
            booking.bookingStatus === 'EXPIRED'
              ? 'The room was released because the booking was not completed in time. You can search again.'
              : 'This booking is no longer active.'
          }
        >
          <LinkButton to="/">Search hotels</LinkButton>
        </EmptyState>
      );
  }
}
