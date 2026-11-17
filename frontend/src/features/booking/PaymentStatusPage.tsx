import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { BookingSummary } from './BookingSummary';
import { useBooking } from './useBooking';

/** How long to keep checking for the payment confirmation before saying it is taking longer than usual. */
export const GIVE_UP_AFTER_MS = 30_000;

/**
 * Where the payment provider sends the user back to, whether they paid or went back without paying. The confirmation reaches
 * the server separately and a moment later, so this page watches the booking until it settles.
 */
export function PaymentStatusPage() {
  const { bookingId: bookingIdParam } = useParams();
  const bookingId = Number(bookingIdParam);
  const valid = Number.isInteger(bookingId) && bookingId >= 1;

  if (!valid) {
    return <EmptyState title="Booking not found" description="This link does not point to a booking." />;
  }
  return <Status bookingId={bookingId} />;
}

function Status({ bookingId }: { bookingId: number }) {
  const query = useBooking(bookingId, { pollWhilePaymentPending: true });
  const [waitedTooLong, setWaitedTooLong] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setWaitedTooLong(true);
    }, GIVE_UP_AFTER_MS);
    return () => {
      clearTimeout(timer);
    };
  }, []);

  if (query.isPending) {
    return (
      <div role="status" aria-label="Checking your payment" className="mx-auto flex max-w-2xl flex-col gap-4">
        <Skeleton className="h-32" />
        <Skeleton className="h-24" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <EmptyState title="We could not check your payment" description={query.error.message}>
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
  const settledMessage = (() => {
    switch (booking.bookingStatus) {
      case 'CONFIRMED':
        return {
          title: 'Payment received. Your booking is confirmed.',
          body: 'We have sent the details to your email.',
        };
      case 'EXPIRED':
      case 'CANCELLED':
        return {
          title: 'This booking is no longer active.',
          body: 'The payment was not completed in time and the room was released. Nothing was charged.',
        };
      case 'PAYMENTS_PENDING':
        return waitedTooLong
          ? {
              title: 'We are still waiting for confirmation.',
              body: 'This can take a minute. If you went back without paying, you can carry on from the booking page.',
            }
          : { title: 'Checking your payment…', body: 'This usually takes a few seconds.' };
      case 'RESERVED':
      case 'GUESTS_ADDED':
        return { title: 'Your payment has not started.', body: 'Continue from the booking page to pay.' };
    }
  })();

  const confirmed = booking.bookingStatus === 'CONFIRMED';

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <Card role="status" aria-live="polite" className={confirmed ? 'border-success' : undefined}>
        <h1 className="text-xl font-semibold text-ink">{settledMessage.title}</h1>
        <p className="mt-1 text-ink-soft">{settledMessage.body}</p>
      </Card>

      <BookingSummary booking={booking} />

      <div className="flex gap-3">
        {confirmed ? (
          <LinkButton to="/account">Go to my account</LinkButton>
        ) : (
          <LinkButton
            to={`/bookings/${String(bookingId)}`}
            variant={booking.bookingStatus === 'PAYMENTS_PENDING' ? 'primary' : 'secondary'}
          >
            {booking.bookingStatus === 'PAYMENTS_PENDING' ? 'Back to my booking' : 'View booking'}
          </LinkButton>
        )}
        <Link
          to="/"
          className="inline-flex h-10 items-center px-2 text-sm text-brand-700 underline underline-offset-4"
        >
          Search more hotels
        </Link>
      </div>
    </div>
  );
}
