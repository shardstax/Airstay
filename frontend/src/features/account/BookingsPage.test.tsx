import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { BookingsPage } from './BookingsPage';

const future = addDays(today(), 30);

const booking = (id: number, overrides: Record<string, unknown> = {}) => ({
  id,
  hotelId: 7,
  hotelName: `Hotel ${String(id)}`,
  roomId: 3,
  roomType: 'Deluxe',
  roomsCount: 1,
  maxGuests: 2,
  checkInDate: future,
  checkOutDate: addDays(future, 2),
  bookingStatus: 'CONFIRMED',
  amount: 5000,
  createdAt: `2026-11-${String(10 + id)}T10:00:00Z`,
  guests: [],
  ...overrides,
});

const render = () => renderRoute(<BookingsPage />);

describe('my bookings', () => {
  it('splits bookings into upcoming and earlier ones', async () => {
    mockApi({
      'GET /users/myBookings': {
        data: [
          booking(1),
          booking(2, { bookingStatus: 'CANCELLED' }),
          booking(3, { checkInDate: addDays(today(), -10), checkOutDate: addDays(today(), -8) }),
        ],
      },
    });
    render();

    const upcoming = await screen.findByRole('region', { name: 'Upcoming and in progress' });
    const earlier = screen.getByRole('region', { name: 'Earlier and closed' });
    expect(within(upcoming).getByText('Hotel 1')).toBeInTheDocument();
    expect(within(earlier).getByText('Hotel 2')).toBeInTheDocument();
    expect(within(earlier).getByText('Hotel 3')).toBeInTheDocument();
  });

  it('shows the newest booking first', async () => {
    mockApi({ 'GET /users/myBookings': { data: [booking(1), booking(5), booking(3)] } });
    render();

    const headings = await screen.findAllByRole('heading', { level: 3 });

    expect(headings.map((heading) => heading.textContent)).toEqual(['Hotel 5', 'Hotel 3', 'Hotel 1']);
  });

  it('shows what was booked, for how long, and the total', async () => {
    mockApi({ 'GET /users/myBookings': { data: [booking(1)] } });
    render();

    expect(await screen.findByText(/Deluxe · 1 room · 2 nights/)).toBeInTheDocument();
    expect(screen.getByText('₹5,000')).toBeInTheDocument();
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
  });

  it('lets the guest carry on with a booking that is still being made', async () => {
    mockApi({ 'GET /users/myBookings': { data: [booking(1, { bookingStatus: 'RESERVED' })] } });
    render();

    expect(await screen.findByRole('link', { name: 'Continue booking' })).toHaveAttribute(
      'href',
      '/bookings/1',
    );
    expect(screen.queryByRole('button', { name: 'Cancel booking' })).not.toBeInTheDocument();
  });

  it('offers to cancel only a confirmed booking whose stay has not started', async () => {
    mockApi({
      'GET /users/myBookings': {
        data: [
          booking(1),
          booking(2, { checkInDate: addDays(today(), -1), checkOutDate: addDays(today(), 3) }),
          booking(3, { bookingStatus: 'EXPIRED' }),
        ],
      },
    });
    render();

    await screen.findByText('Hotel 1');

    expect(screen.getAllByRole('button', { name: 'Cancel booking' })).toHaveLength(1);
  });

  it('asks to confirm before cancelling, and keeps the booking if the guest changes their mind', async () => {
    const backend = mockApi({ 'GET /users/myBookings': { data: [booking(1)] } });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }));
    expect(screen.getByRole('group', { name: 'Confirm cancellation' })).toHaveTextContent(
      '₹5,000 is refunded',
    );

    await user.click(screen.getByRole('button', { name: 'Keep booking' }));

    expect(screen.queryByRole('group', { name: 'Confirm cancellation' })).not.toBeInTheDocument();
    expect(backend.count('POST /bookings/1/cancel')).toBe(0);
  });

  it('cancels, says a refund was requested, and shows the booking as cancelled', async () => {
    let cancelled = false;
    const backend = mockApi({
      'GET /users/myBookings': () => ({
        data: [booking(1, { bookingStatus: cancelled ? 'CANCELLED' : 'CONFIRMED' })],
      }),
      'POST /bookings/1/cancel': () => {
        cancelled = true;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }));
    await user.click(screen.getByRole('button', { name: 'Yes, cancel booking' }));

    expect(
      await screen.findByText(
        'Your booking at Hotel 1 was cancelled. A refund of ₹5,000 has been requested.',
      ),
    ).toBeInTheDocument();
    expect(await screen.findByText('Cancelled', { selector: 'span' })).toBeInTheDocument();
    expect(backend.count('POST /bookings/1/cancel')).toBe(1);
    expect(screen.queryByRole('button', { name: 'Cancel booking' })).not.toBeInTheDocument();
  });

  it('shows the server’s reason when the cancellation is refused', async () => {
    mockApi({
      'GET /users/myBookings': { data: [booking(1)] },
      'POST /bookings/1/cancel': {
        status: 409,
        error: { message: 'Bookings can only be cancelled before the check-in date' },
      },
    });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Cancel booking' }));
    await user.click(screen.getByRole('button', { name: 'Yes, cancel booking' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Bookings can only be cancelled before the check-in date',
    );
    expect(screen.getByRole('button', { name: 'Yes, cancel booking' })).toBeEnabled();
  });

  it('says so when there are no bookings', async () => {
    mockApi({ 'GET /users/myBookings': { data: [] } });
    render();

    expect(await screen.findByRole('heading', { name: 'No bookings yet' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Search hotels' })).toBeInTheDocument();
  });

  it('shows the error and retries', async () => {
    let calls = 0;
    mockApi({
      'GET /users/myBookings': () => {
        calls += 1;
        return calls === 1 ? { status: 500, error: { message: 'Something broke' } } : { data: [booking(1)] };
      },
    });
    const user = userEvent.setup();
    render();

    expect(
      await screen.findByRole('heading', { name: 'We could not load your bookings' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Hotel 1')).toBeInTheDocument();
  });
});
