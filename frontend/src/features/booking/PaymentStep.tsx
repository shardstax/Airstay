import { describeRequestError } from '@/shared/api/errors';
import { formatPrice } from '@/shared/lib/format';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';

import type { Booking } from './bookingStatus';
import { useStartPayment } from './usePayment';

/** Step 2: pay on the payment provider's own page. Used both to start paying and to carry on after coming back. */
export function PaymentStep({ booking }: { booking: Booking }) {
  const pay = useStartPayment(booking.id);
  const resuming = booking.bookingStatus === 'PAYMENTS_PENDING';

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink">
        {resuming ? 'Finish your payment' : 'Pay to confirm'}
      </h2>
      <p className="mt-1 text-sm text-ink-soft">
        {resuming
          ? 'Your payment was started but not completed. You can carry on where you left off.'
          : 'You will be taken to a secure payment page. Your booking is confirmed as soon as the payment goes through.'}
      </p>

      {pay.isError ? (
        <p role="alert" className="mt-4 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
          {describeRequestError(pay.error)}
        </p>
      ) : null}

      <div className="mt-6 flex justify-end">
        <Button
          loading={pay.isPending || pay.isSuccess}
          onClick={() => {
            pay.mutate();
          }}
        >
          {resuming ? 'Continue to payment' : `Pay ${formatPrice(booking.amount)}`}
        </Button>
      </div>
    </Card>
  );
}
