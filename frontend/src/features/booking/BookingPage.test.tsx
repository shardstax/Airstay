import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { navigation } from '@/shared/lib/navigation';
import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute, Where } from '@/test/renderRoute';

import { BookingPage } from './BookingPage';

const checkIn = addDays(today(), 30);
const checkOut = addDays(checkIn, 2);

const booking = (overrides: Record<string, unknown> = {}) => ({
  id: 42,
  hotelId: 7,
  hotelName: 'Lakeview Inn',
  roomId: 3,
  roomType: 'Deluxe',
  roomsCount: 1,
  maxGuests: 2,
  checkInDate: checkIn,
  checkOutDate: checkOut,
  bookingStatus: 'RESERVED',
  amount: 5000,
  expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
  createdAt: new Date().toISOString(),
  guests: [],
  ...overrides,
});

const savedGuests = [
  { id: 1, name: 'Ada Lovelace' },
  { id: 2, name: 'Grace Hopper' },
  { id: 3, name: 'Alan Turing' },
];

function renderBooking(route = '/bookings/42') {
  return renderRoute(
    <>
      <Routes>
        <Route path="/bookings/:bookingId" element={<BookingPage />} />
      </Routes>
      <Where />
    </>,
    { route },
  );
}

describe('a held booking', () => {
  it('shows what was booked, the total and the running hold clock', async () => {
    mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: savedGuests } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'Lakeview Inn' })).toBeInTheDocument();
    expect(screen.getByText(/Deluxe · 1 room/)).toBeInTheDocument();
    expect(screen.getByText('₹5,000')).toBeInTheDocument();
    expect(screen.getByText('Room held')).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveTextContent(/9:5\d|10:00/);
  });

  it('asks who is staying, from the guests the user has saved', async () => {
    mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: savedGuests } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'Who is staying?' })).toBeInTheDocument();
    expect(await screen.findByLabelText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByLabelText('Grace Hopper')).toBeInTheDocument();
    expect(screen.getByText('0 of 2 selected')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDisabled();
  });

  it('does not let more guests be chosen than the room allows', async () => {
    mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: savedGuests } });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByLabelText('Ada Lovelace'));
    await user.click(screen.getByLabelText('Grace Hopper'));

    expect(screen.getByText('2 of 2 selected')).toBeInTheDocument();
    expect(screen.getByLabelText('Alan Turing')).toBeDisabled();

    await user.click(screen.getByLabelText('Ada Lovelace'));
    expect(screen.getByLabelText('Alan Turing')).toBeEnabled();
  });

  it('sends the chosen guests and moves on to payment', async () => {
    const backend = mockApi({
      'GET /bookings/42': { data: booking() },
      'GET /users/guests': { data: savedGuests },
      'POST /bookings/42/addGuests': { data: booking({ bookingStatus: 'GUESTS_ADDED' }) },
    });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByLabelText('Grace Hopper'));
    await user.click(screen.getByRole('button', { name: 'Continue' }));

    expect(await screen.findByRole('heading', { name: 'Pay to confirm' })).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'POST /bookings/42/addGuests')?.body).toEqual([2]);
    expect(screen.getByRole('button', { name: 'Pay ₹5,000' })).toBeInTheDocument();
  });

  it('shows the server’s reason when the guests are refused', async () => {
    mockApi({
      'GET /bookings/42': { data: booking() },
      'GET /users/guests': { data: savedGuests },
      'POST /bookings/42/addGuests': {
        status: 400,
        error: { message: 'This booking allows at most 2 guests' },
      },
    });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByLabelText('Ada Lovelace'));
    await user.click(screen.getByRole('button', { name: 'Continue' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This booking allows at most 2 guests');
    expect(screen.getByRole('heading', { name: 'Who is staying?' })).toBeInTheDocument();
  });
});

describe('adding a new guest', () => {
  it('opens the form straight away when the user has no saved guests', async () => {
    mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: [] } });
    renderBooking();

    expect(await screen.findByRole('form', { name: 'Add a guest' })).toBeInTheDocument();
  });

  it('saves the guest, selects them, and sends only the filled-in fields', async () => {
    const backend = mockApi({
      'GET /bookings/42': { data: booking() },
      'GET /users/guests': { data: [] },
      'POST /users/guests': { status: 201, data: { id: 9, name: 'Katherine Johnson' } },
    });
    const user = userEvent.setup();
    renderBooking();

    await user.type(await screen.findByLabelText('Full name'), '  Katherine Johnson ');
    await user.click(screen.getByRole('button', { name: 'Save guest' }));

    expect(await screen.findByText('1 of 2 selected')).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'POST /users/guests')?.body).toEqual({
      name: 'Katherine Johnson',
    });
  });

  it('sends gender and date of birth when they are given', async () => {
    const backend = mockApi({
      'GET /bookings/42': { data: booking() },
      'GET /users/guests': { data: [] },
      'POST /users/guests': { status: 201, data: { id: 9, name: 'Katherine Johnson' } },
    });
    const user = userEvent.setup();
    renderBooking();

    await user.type(await screen.findByLabelText('Full name'), 'Katherine Johnson');
    await user.selectOptions(screen.getByLabelText('Gender (optional)'), 'FEMALE');
    await user.type(screen.getByLabelText('Date of birth (optional)'), '1918-08-26');
    await user.click(screen.getByRole('button', { name: 'Save guest' }));

    await screen.findByText('1 of 2 selected');
    expect(backend.calls.find((call) => call.key === 'POST /users/guests')?.body).toEqual({
      name: 'Katherine Johnson',
      gender: 'FEMALE',
      dateOfBirth: '1918-08-26',
    });
  });

  it('asks for a name before saving', async () => {
    const backend = mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: [] } });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByRole('button', { name: 'Save guest' }));

    expect(await screen.findByText('Enter the guest’s name.')).toBeInTheDocument();
    expect(backend.count('POST /users/guests')).toBe(0);
  });

  it('refuses a date of birth that is not in the past', async () => {
    const backend = mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: [] } });
    const user = userEvent.setup();
    renderBooking();

    await user.type(await screen.findByLabelText('Full name'), 'Future Person');
    await user.type(screen.getByLabelText('Date of birth (optional)'), addDays(today(), 5));
    await user.click(screen.getByRole('button', { name: 'Save guest' }));

    expect(await screen.findByText('Date of birth must be in the past.')).toBeInTheDocument();
    expect(backend.count('POST /users/guests')).toBe(0);
  });

  it('can be opened and cancelled when there are saved guests', async () => {
    mockApi({ 'GET /bookings/42': { data: booking() }, 'GET /users/guests': { data: savedGuests } });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByRole('button', { name: 'Add a new guest' }));
    expect(screen.getByRole('form', { name: 'Add a guest' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: 'Add a guest' })).not.toBeInTheDocument();
  });
});

describe('paying', () => {
  const withGuests = booking({ bookingStatus: 'GUESTS_ADDED' });

  it('starts the payment and sends the user to the payment page', async () => {
    const redirect = vi.spyOn(navigation, 'redirect').mockImplementation(() => undefined);
    const backend = mockApi({
      'GET /bookings/42': { data: withGuests },
      'POST /bookings/42/payments': { data: { sessionUrl: 'https://pay.example/session/abc' } },
    });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByRole('button', { name: 'Pay ₹5,000' }));

    await vi.waitFor(() => {
      expect(redirect).toHaveBeenCalledWith('https://pay.example/session/abc');
    });
    expect(backend.count('POST /bookings/42/payments')).toBe(1);
  });

  it('shows the reason when the payment cannot be started', async () => {
    const redirect = vi.spyOn(navigation, 'redirect').mockImplementation(() => undefined);
    mockApi({
      'GET /bookings/42': { data: withGuests },
      'POST /bookings/42/payments': {
        status: 502,
        error: { message: 'Could not start payment, please try again' },
      },
    });
    const user = userEvent.setup();
    renderBooking();

    await user.click(await screen.findByRole('button', { name: 'Pay ₹5,000' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not start payment, please try again');
    expect(redirect).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Pay ₹5,000' })).toBeEnabled();
  });

  it('lets the user carry on with a payment they started but did not finish', async () => {
    mockApi({ 'GET /bookings/42': { data: booking({ bookingStatus: 'PAYMENTS_PENDING' }) } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'Finish your payment' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continue to payment' })).toBeInTheDocument();
    expect(screen.getByText('Payment pending')).toBeInTheDocument();
  });
});

describe('a booking that has ended or is done', () => {
  it('confirms a paid booking and shows no hold clock', async () => {
    mockApi({ 'GET /bookings/42': { data: booking({ bookingStatus: 'CONFIRMED', expiresAt: undefined }) } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'Your booking is confirmed' })).toBeInTheDocument();
    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to my account' })).toBeInTheDocument();
  });

  it('explains a hold that ran out', async () => {
    mockApi({ 'GET /bookings/42': { data: booking({ bookingStatus: 'EXPIRED', expiresAt: undefined }) } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'This hold has ended' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Search hotels' })).toBeInTheDocument();
  });

  it('explains a cancelled booking', async () => {
    mockApi({ 'GET /bookings/42': { data: booking({ bookingStatus: 'CANCELLED', expiresAt: undefined }) } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'This booking was cancelled' })).toBeInTheDocument();
  });

  it('asks the server again when the hold clock reaches zero', async () => {
    let calls = 0;
    const backend = mockApi({
      'GET /bookings/42': () => {
        calls += 1;
        return calls === 1
          ? { data: booking({ expiresAt: new Date(Date.now() + 1500).toISOString() }) }
          : { data: booking({ bookingStatus: 'EXPIRED', expiresAt: undefined }) };
      },
      'GET /users/guests': { data: savedGuests },
    });
    renderBooking();

    expect(
      await screen.findByRole('heading', { name: 'This hold has ended' }, { timeout: 4000 }),
    ).toBeInTheDocument();
    expect(backend.count('GET /bookings/42')).toBe(2);
  });
});

describe('when the booking cannot be shown', () => {
  it.each([404, 403])('says it was not found for a %i', async (status) => {
    mockApi({ 'GET /bookings/42': { status, error: { message: 'nope' } } });
    renderBooking();

    expect(await screen.findByRole('heading', { name: 'Booking not found' })).toBeInTheDocument();
  });

  it('offers a retry for any other failure', async () => {
    let calls = 0;
    mockApi({
      'GET /bookings/42': () => {
        calls += 1;
        return calls === 1
          ? { status: 500, error: { message: 'Something broke' } }
          : { data: booking({ bookingStatus: 'CONFIRMED' }) };
      },
    });
    const user = userEvent.setup();
    renderBooking();

    expect(
      await screen.findByRole('heading', { name: 'We could not load your booking' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { name: 'Your booking is confirmed' })).toBeInTheDocument();
  });

  it.each(['abc', '0', '-1', '1.5'])('rejects the id "%s" without calling the API', (id) => {
    const backend = mockApi({});
    renderBooking(`/bookings/${id}`);

    expect(screen.getByRole('heading', { name: 'Booking not found' })).toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });

  it('shows a loading state first', async () => {
    let release: () => void = () => undefined;
    mockApi({
      'GET /bookings/42': () =>
        new Promise((resolve) => {
          release = () => {
            resolve({ data: booking({ bookingStatus: 'CONFIRMED' }) });
          };
        }),
    });
    renderBooking();

    expect(await screen.findByRole('status', { name: 'Loading your booking' })).toBeInTheDocument();
    release();
    await screen.findByRole('heading', { name: 'Your booking is confirmed' });
    expect(
      within(document.body).queryByRole('status', { name: 'Loading your booking' }),
    ).not.toBeInTheDocument();
  });
});
