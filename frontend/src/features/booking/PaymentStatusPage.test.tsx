import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { GIVE_UP_AFTER_MS, PaymentStatusPage } from './PaymentStatusPage';
import { POLL_INTERVAL_MS } from './useBooking';

const checkIn = addDays(today(), 30);

const booking = (bookingStatus: string) => ({
  id: 42,
  hotelId: 7,
  hotelName: 'Lakeview Inn',
  roomId: 3,
  roomType: 'Deluxe',
  roomsCount: 1,
  maxGuests: 2,
  checkInDate: checkIn,
  checkOutDate: addDays(checkIn, 2),
  bookingStatus,
  amount: 5000,
  guests: [],
});

function renderStatus(route = '/payments/42/status') {
  return renderRoute(
    <Routes>
      <Route path="/payments/:bookingId/status" element={<PaymentStatusPage />} />
    </Routes>,
    { route },
  );
}

describe('the page the payment provider sends the user back to', () => {
  it('confirms a booking that is already paid', async () => {
    mockApi({ 'GET /bookings/42': { data: booking('CONFIRMED') } });
    renderStatus();

    expect(await screen.findByText('Payment received. Your booking is confirmed.')).toBeInTheDocument();
    expect(screen.getByText('Lakeview Inn')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to my account' })).toBeInTheDocument();
  });

  it('explains a booking that is no longer active, and that nothing was charged', async () => {
    mockApi({ 'GET /bookings/42': { data: booking('EXPIRED') } });
    renderStatus();

    expect(await screen.findByText('This booking is no longer active.')).toBeInTheDocument();
    expect(screen.getByText(/Nothing was charged/)).toBeInTheDocument();
  });

  it('points a user who has not paid back to the booking', async () => {
    mockApi({ 'GET /bookings/42': { data: booking('GUESTS_ADDED') } });
    renderStatus();

    expect(await screen.findByText('Your payment has not started.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'View booking' })).toHaveAttribute('href', '/bookings/42');
  });

  it('rejects an invalid id without calling the API', () => {
    const backend = mockApi({});
    renderStatus('/payments/abc/status');

    expect(screen.getByRole('heading', { name: 'Booking not found' })).toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });

  it('offers a retry when the check itself fails', async () => {
    let calls = 0;
    mockApi({
      'GET /bookings/42': () => {
        calls += 1;
        return calls === 1
          ? { status: 500, error: { message: 'Something broke' } }
          : { data: booking('CONFIRMED') };
      },
    });
    const user = userEvent.setup();
    renderStatus();

    expect(
      await screen.findByRole('heading', { name: 'We could not check your payment' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Payment received. Your booking is confirmed.')).toBeInTheDocument();
  });
});

describe('waiting for the payment confirmation', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps checking while the payment is pending and shows the confirmation when it arrives', async () => {
    let calls = 0;
    const backend = mockApi({
      'GET /bookings/42': () => {
        calls += 1;
        return { data: booking(calls < 3 ? 'PAYMENTS_PENDING' : 'CONFIRMED') };
      },
    });
    renderStatus();

    expect(await screen.findByText('Checking your payment…')).toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2 + 200);
    });

    expect(await screen.findByText('Payment received. Your booking is confirmed.')).toBeInTheDocument();
    expect(backend.count('GET /bookings/42')).toBe(3);
  });

  it('stops checking once the booking has settled', async () => {
    const backend = mockApi({ 'GET /bookings/42': { data: booking('CONFIRMED') } });
    renderStatus();
    await screen.findByText('Payment received. Your booking is confirmed.');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 5);
    });

    expect(backend.count('GET /bookings/42')).toBe(1);
  });

  it('says it is taking longer than usual after a while, and offers the way back', async () => {
    mockApi({ 'GET /bookings/42': { data: booking('PAYMENTS_PENDING') } });
    renderStatus();
    await screen.findByText('Checking your payment…');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(GIVE_UP_AFTER_MS + 500);
    });

    expect(await screen.findByText('We are still waiting for confirmation.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to my booking' })).toHaveAttribute('href', '/bookings/42');
  });
});
