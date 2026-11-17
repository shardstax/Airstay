import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { HotelDashboardPage } from './HotelDashboardPage';

const catalog = {
  hotelAmenities: ['WiFi'],
  roomAmenities: ['Balcony', 'TV'],
  roomTypes: ['Standard', 'Deluxe', 'Suite'],
};
const hotel = (overrides: Record<string, unknown> = {}) => ({
  id: 7,
  name: 'Lakeview Inn',
  city: 'Pune, Maharashtra, India',
  placeId: 1259229,
  active: true,
  ...overrides,
});
const deluxe = { id: 3, type: 'Deluxe', basePrice: 2500, totalCount: 5, capacity: 2, amenities: ['Balcony'] };
const suite = { id: 4, type: 'Suite', basePrice: 4500, totalCount: 2, capacity: 4 };
const booking = (id: number, status = 'CONFIRMED') => ({
  id,
  hotelId: 7,
  hotelName: 'Lakeview Inn',
  roomId: 3,
  roomType: 'Deluxe',
  roomsCount: 1,
  maxGuests: 2,
  checkInDate: addDays(today(), 20),
  checkOutDate: addDays(today(), 22),
  bookingStatus: status,
  amount: 5000,
});

const live = {
  'GET /catalog': { data: catalog },
  'GET /admin/hotels/7': { data: hotel() },
  'GET /admin/hotels/7/rooms': { data: [deluxe, suite] },
  'GET /admin/hotels/7/bookings': { data: [booking(1), booking(2, 'CANCELLED')] },
  'GET /admin/hotels/7/reports': { data: { bookingCount: 12, totalRevenue: 60000, avgRevenue: 5000 } },
};

function renderDashboard(route = '/manage/hotels/7') {
  return renderRoute(
    <Routes>
      <Route path="/manage/hotels/:hotelId" element={<HotelDashboardPage />} />
    </Routes>,
    { route },
  );
}

describe('a live hotel', () => {
  it('shows the hotel, that it is on sale, and the way to edit it', async () => {
    mockApi(live);
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Lakeview Inn', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('Pune, Maharashtra, India')).toBeInTheDocument();
    expect(screen.getByText('On sale')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Edit details' })).toHaveAttribute(
      'href',
      '/manage/hotels/7/edit',
    );
    expect(screen.queryByRole('heading', { name: 'Put this hotel on sale' })).not.toBeInTheDocument();
  });

  it('lists the room types with their prices and links to their prices and availability', async () => {
    mockApi(live);
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Deluxe' })).toBeInTheDocument();
    expect(screen.getByText('₹2,500')).toBeInTheDocument();
    expect(screen.getByText('5 rooms · up to 2 guests each')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Prices and availability' })[0]).toHaveAttribute(
      'href',
      '/manage/hotels/7/rooms/3/inventory',
    );
  });

  it('shows the last 30 days as three numbers', async () => {
    const backend = mockApi(live);
    renderDashboard();

    expect(await screen.findByText('Confirmed bookings')).toBeInTheDocument();
    const stats = screen.getByText('Confirmed bookings').closest('div');
    expect(stats).toHaveTextContent('12');
    expect(screen.getByText('₹60,000')).toBeInTheDocument();
    expect(screen.getByText('Average per booking').closest('div')).toHaveTextContent('₹5,000');
    const query = backend.calls.find((call) => call.key === 'GET /admin/hotels/7/reports')?.query;
    expect(query?.get('endDate')).toBe(today());
    expect(query?.get('startDate')).toBe(addDays(today(), -30));
  });

  it('lists the bookings, newest first, with their status', async () => {
    mockApi(live);
    renderDashboard();

    const table = await screen.findByRole('table', { name: 'Bookings at this hotel' });
    const rows = within(table).getAllByRole('row').slice(1);

    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('#2');
    expect(rows[0]).toHaveTextContent('Cancelled');
    expect(rows[1]).toHaveTextContent('#1');
    expect(rows[1]).toHaveTextContent('Confirmed');
  });

  it('says so when there are no bookings yet', async () => {
    mockApi({ ...live, 'GET /admin/hotels/7/bookings': { data: [] } });
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'No bookings yet' })).toBeInTheDocument();
  });
});

describe('a draft hotel', () => {
  const draft = { ...live, 'GET /admin/hotels/7': { data: hotel({ active: false }) } };

  it('cannot be put on sale until it has a room type', async () => {
    mockApi({ ...draft, 'GET /admin/hotels/7/rooms': { data: [] } });
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Put this hotel on sale' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Put on sale' })).toBeDisabled();
    expect(screen.getByText(/Add at least one room type below/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Last 30 days' })).not.toBeInTheDocument();
  });

  it('is put on sale, and then shows as on sale', async () => {
    let active = false;
    const backend = mockApi({
      ...draft,
      'GET /admin/hotels/7': () => ({ data: hotel({ active }) }),
      'PATCH /admin/hotels/7/activate': () => {
        active = true;
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Put on sale' }));

    expect(await screen.findByText('On sale')).toBeInTheDocument();
    expect(backend.count('PATCH /admin/hotels/7/activate')).toBe(1);
    expect(screen.queryByRole('heading', { name: 'Put this hotel on sale' })).not.toBeInTheDocument();
  });

  it('shows why putting it on sale failed', async () => {
    mockApi({
      ...draft,
      'PATCH /admin/hotels/7/activate': { status: 409, error: { message: 'Add rooms before activating' } },
    });
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Put on sale' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Add rooms before activating');
  });

  it('does not offer prices and availability before the hotel is on sale', async () => {
    mockApi(draft);
    renderDashboard();

    await screen.findByRole('heading', { name: 'Deluxe' });

    expect(screen.queryByRole('link', { name: 'Prices and availability' })).not.toBeInTheDocument();
  });
});

describe('room types', () => {
  it('adds one, choosing the type and amenities from the lists', async () => {
    const backend = mockApi({ ...live, 'POST /admin/hotels/7/rooms': { status: 201, data: { id: 5 } } });
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Add a room type' }));
    const form = screen.getByRole('form', { name: 'Add a room type' });
    await user.selectOptions(within(form).getByLabelText('Room type'), 'Standard');
    await user.type(within(form).getByLabelText('Base price per night (₹)'), '1800.50');
    await user.type(within(form).getByLabelText('Rooms of this type'), '8');
    await user.type(within(form).getByLabelText('Guests per room'), '3');
    await user.click(within(form).getByLabelText('TV'));
    await user.click(within(form).getByRole('button', { name: 'Add room type' }));

    await vi.waitFor(() => {
      expect(screen.queryByRole('form', { name: 'Add a room type' })).not.toBeInTheDocument();
    });
    expect(backend.calls.find((call) => call.key === 'POST /admin/hotels/7/rooms')?.body).toEqual({
      type: 'Standard',
      basePrice: 1800.5,
      totalCount: 8,
      capacity: 3,
      amenities: ['TV'],
    });
  });

  it('only offers the catalog’s room types', async () => {
    mockApi(live);
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Add a room type' }));
    const options = within(screen.getByLabelText('Room type'))
      .getAllByRole('option')
      .map((o) => o.textContent);

    expect(options).toEqual(['Choose a type', 'Standard', 'Deluxe', 'Suite']);
  });

  it('refuses numbers outside the limits, without calling the server', async () => {
    const backend = mockApi(live);
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Add a room type' }));
    await user.selectOptions(screen.getByLabelText('Room type'), 'Suite');
    await user.type(screen.getByLabelText('Base price per night (₹)'), '0');
    await user.type(screen.getByLabelText('Rooms of this type'), '2000');
    await user.type(screen.getByLabelText('Guests per room'), '50');
    await user.click(screen.getByRole('button', { name: 'Add room type' }));

    expect(await screen.findByText('The price must be more than zero.')).toBeInTheDocument();
    expect(screen.getByText('At most 1000 rooms.')).toBeInTheDocument();
    expect(screen.getByText('At most 20 guests per room.')).toBeInTheDocument();
    expect(backend.count('POST /admin/hotels/7/rooms')).toBe(0);
  });

  it('asks for a type to be chosen', async () => {
    mockApi(live);
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Add a room type' }));
    await user.click(screen.getByRole('button', { name: 'Add room type' }));

    expect(await screen.findByText('Choose a room type from the list.')).toBeInTheDocument();
  });

  it('edits one, starting from what is saved', async () => {
    const backend = mockApi({ ...live, 'PUT /admin/hotels/7/rooms/3': { data: deluxe } });
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Edit Deluxe' }));
    const form = screen.getByRole('form', { name: 'Edit Deluxe' });
    expect(within(form).getByLabelText('Room type')).toHaveValue('Deluxe');
    expect(within(form).getByLabelText('Base price per night (₹)')).toHaveValue(2500);

    await user.clear(within(form).getByLabelText('Base price per night (₹)'));
    await user.type(within(form).getByLabelText('Base price per night (₹)'), '2750');
    await user.click(within(form).getByRole('button', { name: 'Save changes' }));

    await vi.waitFor(() => {
      expect(backend.count('PUT /admin/hotels/7/rooms/3')).toBe(1);
    });
    expect(backend.calls.find((call) => call.key === 'PUT /admin/hotels/7/rooms/3')?.body).toMatchObject({
      type: 'Deluxe',
      basePrice: 2750,
    });
  });

  it('asks before deleting one, and deletes on confirmation', async () => {
    const backend = mockApi({ ...live, 'DELETE /admin/hotels/7/rooms/4': { status: 204 } });
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Delete Suite' }));
    expect(screen.getByRole('group', { name: 'Confirm deleting Suite' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }));

    await vi.waitFor(() => {
      expect(backend.count('DELETE /admin/hotels/7/rooms/4')).toBe(1);
    });
  });

  it('shows the server’s reason when a room type cannot be deleted', async () => {
    mockApi({
      ...live,
      'DELETE /admin/hotels/7/rooms/4': {
        status: 409,
        error: { message: 'Room types with bookings cannot be deleted' },
      },
    });
    const user = userEvent.setup();
    renderDashboard();

    await user.click(await screen.findByRole('button', { name: 'Delete Suite' }));
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Room types with bookings cannot be deleted');
  });
});

describe('when the hotel cannot be shown', () => {
  it.each([404, 403])('says it was not found for a %i', async (status) => {
    mockApi({ ...live, 'GET /admin/hotels/7': { status, error: { message: 'nope' } } });
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Hotel not found' })).toBeInTheDocument();
  });

  it.each(['abc', '0', '-2'])('rejects the id "%s" without calling the API', (id) => {
    const backend = mockApi({});
    renderDashboard(`/manage/hotels/${id}`);

    expect(screen.getByRole('heading', { name: 'Hotel not found' })).toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });
});
