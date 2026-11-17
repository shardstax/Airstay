import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { InventoryPage } from './InventoryPage';

const night = (offset: number, overrides: Record<string, unknown> = {}) => ({
  id: 1000 + offset,
  date: addDays(today(), offset),
  bookedCount: 1,
  reservedCount: 1,
  totalCount: 5,
  surgeFactor: 1,
  price: 2500,
  closed: false,
  ...overrides,
});

function renderInventory(route = '/manage/hotels/7/rooms/3/inventory') {
  return renderRoute(
    <Routes>
      <Route path="/manage/hotels/:hotelId/rooms/:roomId/inventory" element={<InventoryPage />} />
    </Routes>,
    { route },
  );
}

describe('prices and availability', () => {
  it('lists upcoming nights with price, free rooms and status, hiding nights already past', async () => {
    mockApi({
      'GET /admin/inventory/rooms/3': {
        data: [
          night(-2),
          night(1, { surgeFactor: 1.5, price: 3750 }),
          night(2, { closed: true, bookedCount: 5, reservedCount: 0 }),
        ],
      },
    });
    renderInventory();

    const table = await screen.findByRole('table', { name: 'Nightly prices and availability' });
    const rows = within(table).getAllByRole('row').slice(1);

    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('₹3,750');
    expect(rows[0]).toHaveTextContent('x1.5');
    expect(rows[0]).toHaveTextContent('3 of 5');
    expect(rows[0]).toHaveTextContent('Open');
    expect(rows[1]).toHaveTextContent('0 of 5');
    expect(rows[1]).toHaveTextContent('Closed');
  });

  it('shows thirty nights at first and more on request', async () => {
    mockApi({ 'GET /admin/inventory/rooms/3': { data: Array.from({ length: 45 }, (_, i) => night(i + 1)) } });
    const user = userEvent.setup();
    renderInventory();

    const table = await screen.findByRole('table');
    expect(within(table).getAllByRole('row')).toHaveLength(31);

    await user.click(screen.getByRole('button', { name: 'Show more nights' }));

    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(46);
    expect(screen.queryByRole('button', { name: 'Show more nights' })).not.toBeInTheDocument();
  });

  it('applies a price factor to a range of nights', async () => {
    const backend = mockApi({
      'GET /admin/inventory/rooms/3': { data: [night(1)] },
      'PATCH /admin/inventory/rooms/3': { status: 204 },
    });
    const user = userEvent.setup();
    renderInventory();

    await screen.findByRole('table');
    const form = screen.getByRole('form', { name: 'Change nights' });
    fireEvent.change(within(form).getByLabelText('First night'), { target: { value: addDays(today(), 10) } });
    fireEvent.change(within(form).getByLabelText('Last night'), { target: { value: addDays(today(), 12) } });
    await user.clear(within(form).getByLabelText('Price factor'));
    await user.type(within(form).getByLabelText('Price factor'), '1.75');
    await user.click(within(form).getByRole('button', { name: 'Apply to these nights' }));

    expect(await screen.findByText('The nights were updated.')).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'PATCH /admin/inventory/rooms/3')?.body).toEqual({
      startDate: addDays(today(), 10),
      endDate: addDays(today(), 12),
      surgeFactor: 1.75,
      closed: false,
    });
  });

  it('closes a range of nights', async () => {
    const backend = mockApi({
      'GET /admin/inventory/rooms/3': { data: [night(1)] },
      'PATCH /admin/inventory/rooms/3': { status: 204 },
    });
    const user = userEvent.setup();
    renderInventory();

    await screen.findByRole('table');
    await user.click(screen.getByLabelText('Close these nights for booking'));
    await user.click(screen.getByRole('button', { name: 'Apply to these nights' }));

    await screen.findByText('The nights were updated.');
    expect(backend.calls.find((call) => call.key === 'PATCH /admin/inventory/rooms/3')?.body).toMatchObject({
      closed: true,
    });
  });

  it('refuses a price factor outside the allowed range, without calling the server', async () => {
    const backend = mockApi({ 'GET /admin/inventory/rooms/3': { data: [night(1)] } });
    const user = userEvent.setup();
    renderInventory();

    await screen.findByRole('table');
    await user.clear(screen.getByLabelText('Price factor'));
    await user.type(screen.getByLabelText('Price factor'), '9');
    await user.click(screen.getByRole('button', { name: 'Apply to these nights' }));

    expect(await screen.findByText('The factor must be at most 5.')).toBeInTheDocument();
    expect(backend.count('PATCH /admin/inventory/rooms/3')).toBe(0);
  });

  it('refuses a last night before the first', async () => {
    mockApi({ 'GET /admin/inventory/rooms/3': { data: [night(1)] } });
    const user = userEvent.setup();
    renderInventory();

    await screen.findByRole('table');
    fireEvent.change(screen.getByLabelText('First night'), { target: { value: addDays(today(), 10) } });
    fireEvent.change(screen.getByLabelText('Last night'), { target: { value: addDays(today(), 8) } });
    await user.click(screen.getByRole('button', { name: 'Apply to these nights' }));

    expect(await screen.findByText('The last night cannot be before the first.')).toBeInTheDocument();
  });

  it('shows the server’s reason when the change is refused', async () => {
    mockApi({
      'GET /admin/inventory/rooms/3': { data: [night(1)] },
      'PATCH /admin/inventory/rooms/3': { status: 403, error: { message: 'You are not the owner' } },
    });
    const user = userEvent.setup();
    renderInventory();

    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Apply to these nights' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('You are not the owner');
  });

  it('says what to do when there are no nights yet', async () => {
    mockApi({ 'GET /admin/inventory/rooms/3': { data: [] } });
    renderInventory();

    expect(await screen.findByRole('heading', { name: 'No upcoming nights' })).toBeInTheDocument();
  });

  it('links back to the hotel', async () => {
    mockApi({ 'GET /admin/inventory/rooms/3': { data: [] } });
    renderInventory();

    expect(await screen.findByRole('link', { name: 'Back to the hotel' })).toHaveAttribute(
      'href',
      '/manage/hotels/7',
    );
  });

  it.each(['/manage/hotels/abc/rooms/3/inventory', '/manage/hotels/7/rooms/0/inventory'])(
    'rejects the address %s without calling the API',
    (route) => {
      const backend = mockApi({});
      renderInventory(route);

      expect(screen.getByRole('heading', { name: 'Room type not found' })).toBeInTheDocument();
      expect(backend.calls).toHaveLength(0);
    },
  );
});
