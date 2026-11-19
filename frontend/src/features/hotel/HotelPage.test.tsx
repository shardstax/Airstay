import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { addDays, today } from '@/shared/lib/stay';
import { ada, mockApi, rememberPreviousSession } from '@/test/mockApi';
import { renderRoute, Where } from '@/test/renderRoute';

import { HotelPage } from './HotelPage';

const startDate = addDays(today(), 30);
const endDate = addDays(startDate, 2);
const hotelUrl = (id: string | number = 7, rooms = 1) =>
  `/hotels/${String(id)}?startDate=${startDate}&endDate=${endDate}&rooms=${String(rooms)}`;

const info = (rooms: unknown[], hotel: Record<string, unknown> = {}) => ({
  hotel: { id: 7, name: 'Lakeview Inn', city: 'Pune', active: true, ...hotel },
  rooms,
});
const room = (id: number, type: string, price: number, amenities: string[] = []) => ({
  id,
  type,
  price,
  amenities,
});

function renderHotel(route: string) {
  return renderRoute(
    <>
      <Routes>
        <Route path="/hotels/:hotelId" element={<HotelPage />} />
      </Routes>
      <Where />
    </>,
    { route },
  );
}

describe('the hotel page', () => {
  it('shows the hotel and its available rooms with prices for the stay', async () => {
    mockApi({
      'GET /hotels/7/info': {
        data: info([room(1, 'Deluxe', 2500, ['AC', 'Balcony']), room(2, 'Suite', 4000)], {
          amenities: ['WiFi', 'Pool'],
          contactInfo: { address: '12 MG Road' },
        }),
      },
    });
    renderHotel(hotelUrl());

    expect(await screen.findByRole('heading', { name: 'Lakeview Inn' })).toBeInTheDocument();
    expect(screen.getByText('12 MG Road, Pune')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Deluxe' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Suite' })).toBeInTheDocument();
    expect(screen.getByText(/2 nights · 1 room/)).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Deluxe amenities' })).toHaveTextContent('AC');
  });

  it('shows the price per night and the total for the whole stay', async () => {
    mockApi({ 'GET /hotels/7/info': { data: info([room(1, 'Deluxe', 2500)]) } });
    renderHotel(hotelUrl());

    expect(await screen.findByText('₹2,500', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('₹5,000 for 2 nights')).toBeInTheDocument();
  });

  it('multiplies the total by the number of rooms', async () => {
    mockApi({ 'GET /hotels/7/info': { data: info([room(1, 'Deluxe', 2500)]) } });
    renderHotel(hotelUrl(7, 3));

    expect(await screen.findByText('₹15,000 for 2 nights, 3 rooms')).toBeInTheDocument();
  });

  it('asks the API for the dates and room count in the URL', async () => {
    const backend = mockApi({ 'GET /hotels/7/info': { data: info([room(1, 'Deluxe', 2500)]) } });
    renderHotel(hotelUrl(7, 2));

    await screen.findByRole('heading', { name: 'Lakeview Inn' });

    expect(Object.fromEntries(backend.calls[0]?.query ?? [])).toEqual({
      startDate,
      endDate,
      roomsCount: '2',
    });
  });

  it('links back to the search with the dates, city and selected amenities', async () => {
    mockApi({ 'GET /hotels/7/info': { data: info([room(1, 'Deluxe', 2500)]) } });
    renderHotel(`${hotelUrl()}&amenities=Gym&amenities=Breakfast`);

    const back = await screen.findByRole('link', { name: 'Back to results' });
    const params = new URLSearchParams(back.getAttribute('href')?.split('?')[1]);

    expect(params.get('city')).toBe('Pune');
    expect(params.get('startDate')).toBe(startDate);
    expect(params.get('endDate')).toBe(endDate);
    expect(params.getAll('amenities')).toEqual(['Gym', 'Breakfast']);
  });

  it('explains when no room type is free for the whole stay', async () => {
    mockApi({ 'GET /hotels/7/info': { data: info([]) } });
    renderHotel(hotelUrl());

    expect(
      await screen.findByRole('heading', { name: 'No rooms available for these dates' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Change your search' })).toBeInTheDocument();
  });

  it('shows a loading state while it waits', async () => {
    let release: () => void = () => undefined;
    mockApi({
      'GET /hotels/7/info': () =>
        new Promise((resolve) => {
          release = () => {
            resolve({ data: info([room(1, 'Deluxe', 2500)]) });
          };
        }),
    });
    renderHotel(hotelUrl());

    expect(await screen.findByRole('status', { name: 'Loading hotel' })).toBeInTheDocument();
    release();
    expect(await screen.findByRole('heading', { name: 'Lakeview Inn' })).toBeInTheDocument();
  });

  it('says the hotel was not found when the server answers 404', async () => {
    mockApi({ 'GET /hotels/7/info': { status: 404, error: { message: 'Hotel not found with ID: 7' } } });
    renderHotel(hotelUrl());

    expect(await screen.findByRole('heading', { name: 'Hotel not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Search hotels' })).toBeInTheDocument();
  });

  it('offers a retry for any other failure', async () => {
    let calls = 0;
    mockApi({
      'GET /hotels/7/info': () => {
        calls += 1;
        return calls === 1
          ? { status: 500, error: { message: 'Something broke' } }
          : { data: info([room(1, 'Deluxe', 2500)]) };
      },
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    expect(await screen.findByRole('heading', { name: 'We could not load this hotel' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { name: 'Lakeview Inn' })).toBeInTheDocument();
  });

  it('asks for dates when the link has none, without calling the API', () => {
    const backend = mockApi({});
    renderHotel('/hotels/7');

    expect(screen.getByRole('heading', { name: 'Choose your dates' })).toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });

  it.each(['abc', '0', '-3', '1.5'])('rejects the hotel id "%s" without calling the API', (id) => {
    const backend = mockApi({});
    renderHotel(hotelUrl(id));

    expect(screen.getByRole('heading', { name: 'Hotel not found' })).toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });
});

describe('reserving a room', () => {
  const signedIn = {
    'POST /auth/refresh': { data: { accessToken: 'token' } },
    'GET /users/profile': { data: ada },
  };
  const offer = { 'GET /hotels/7/info': { data: info([room(3, 'Deluxe', 2500), room(4, 'Suite', 4000)]) } };
  const reserveButton = (name: string) => screen.findByRole('button', { name: `Reserve ${name}` });

  it('sends a visitor who is not signed in to sign in, and brings them back here afterwards', async () => {
    const backend = mockApi(offer);
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    await user.click(await reserveButton('Deluxe'));

    expect(backend.count('POST /bookings/init')).toBe(0);
    expect(await screen.findByText('/login')).toBeInTheDocument();
  });

  it('holds the chosen room for the stay and opens the booking', async () => {
    rememberPreviousSession();
    const backend = mockApi({
      ...offer,
      ...signedIn,
      'POST /bookings/init': { status: 201, data: { id: 42 } },
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl(7, 2));

    await user.click(await reserveButton('Suite'));

    await vi.waitFor(() => {
      expect(screen.getByTestId('where')).toHaveTextContent('/bookings/42');
    });
    expect(backend.calls.find((call) => call.key === 'POST /bookings/init')?.body).toEqual({
      hotelId: 7,
      roomId: 4,
      checkInDate: startDate,
      checkOutDate: endDate,
      roomsCount: 2,
    });
  });

  it('sends an idempotency key so a retry cannot hold a second room', async () => {
    rememberPreviousSession();
    const backend = mockApi({
      ...offer,
      ...signedIn,
      'POST /bookings/init': { status: 201, data: { id: 42 } },
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    await user.click(await reserveButton('Deluxe'));
    await screen.findByText('/bookings/42');

    const key = backend.calls
      .find((call) => call.key === 'POST /bookings/init')
      ?.headers.get('idempotency-key');
    expect(key).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('places one hold even if the button is clicked twice', async () => {
    rememberPreviousSession();
    let release: () => void = () => undefined;
    const backend = mockApi({
      ...offer,
      ...signedIn,
      'POST /bookings/init': () =>
        new Promise((resolve) => {
          release = () => {
            resolve({ status: 201, data: { id: 42 } });
          };
        }),
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    const button = await reserveButton('Deluxe');
    await user.click(button);
    await user.click(button);
    release();

    await screen.findByText('/bookings/42');
    expect(backend.count('POST /bookings/init')).toBe(1);
  });

  it('says so when someone else took the last room, and allows another try with a fresh key', async () => {
    rememberPreviousSession();
    let attempts = 0;
    const backend = mockApi({
      ...offer,
      ...signedIn,
      'POST /bookings/init': () => {
        attempts += 1;
        return attempts === 1
          ? { status: 409, error: { message: 'Room is not available for the selected dates' } }
          : { status: 201, data: { id: 42 } };
      },
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    await user.click(await reserveButton('Deluxe'));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Room is not available for the selected dates',
    );

    await user.click(screen.getByRole('button', { name: 'Reserve Deluxe' }));
    await screen.findByText('/bookings/42');

    const keys = backend.calls
      .filter((call) => call.key === 'POST /bookings/init')
      .map((call) => call.headers.get('idempotency-key'));
    expect(keys).toHaveLength(2);
    expect(keys[0]).not.toEqual(keys[1]);
  });

  it('keeps the same key when there was no answer at all, so a lost response cannot cause a duplicate', async () => {
    rememberPreviousSession();
    let attempts = 0;
    const backend = mockApi({
      ...offer,
      ...signedIn,
      'POST /bookings/init': () => {
        attempts += 1;
        if (attempts === 1) {
          throw new TypeError('Failed to fetch');
        }
        return { status: 201, data: { id: 42 } };
      },
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    await user.click(await reserveButton('Deluxe'));
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the server/i);
    await user.click(screen.getByRole('button', { name: 'Reserve Deluxe' }));
    await screen.findByText('/bookings/42');

    const keys = backend.calls
      .filter((call) => call.key === 'POST /bookings/init')
      .map((call) => call.headers.get('idempotency-key'));
    expect(keys[0]).toBe(keys[1]);
  });

  it('explains rate limiting', async () => {
    rememberPreviousSession();
    mockApi({
      ...offer,
      ...signedIn,
      'POST /bookings/init': { status: 429, error: { message: 'Too many bookings' } },
    });
    const user = userEvent.setup();
    renderHotel(hotelUrl());

    await user.click(await reserveButton('Deluxe'));

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many attempts/i);
  });
});
