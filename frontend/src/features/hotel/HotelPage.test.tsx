import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

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
    <Routes>
      <Route path="/hotels/:hotelId" element={<HotelPage />} />
    </Routes>,
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

  it('links back to the search, with the same dates and the hotel’s city', async () => {
    mockApi({ 'GET /hotels/7/info': { data: info([room(1, 'Deluxe', 2500)]) } });
    renderHotel(hotelUrl());

    const back = await screen.findByRole('link', { name: 'Back to results' });
    const params = new URLSearchParams(back.getAttribute('href')?.split('?')[1]);

    expect(params.get('city')).toBe('Pune');
    expect(params.get('startDate')).toBe(startDate);
    expect(params.get('endDate')).toBe(endDate);
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
