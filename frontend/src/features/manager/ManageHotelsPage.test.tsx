import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { ManageHotelsPage } from './ManageHotelsPage';

const hotel = (id: number, overrides: Record<string, unknown> = {}) => ({
  id,
  name: `Hotel ${String(id)}`,
  city: 'Pune, Maharashtra, India',
  placeId: 1259229,
  active: true,
  ...overrides,
});

const render = () => renderRoute(<ManageHotelsPage />);

describe('my hotels', () => {
  it('lists the hotels with where they are and whether they are on sale', async () => {
    mockApi({ 'GET /admin/hotels': { data: [hotel(1), hotel(2, { active: false })] } });
    render();

    expect(await screen.findByRole('link', { name: 'Hotel 1' })).toHaveAttribute('href', '/manage/hotels/1');
    expect(screen.getAllByText('Pune, Maharashtra, India')).toHaveLength(2);
    expect(screen.getByText('On sale')).toBeInTheDocument();
    expect(screen.getByText('Draft')).toBeInTheDocument();
  });

  it('offers to add a hotel', async () => {
    mockApi({ 'GET /admin/hotels': { data: [hotel(1)] } });
    render();

    expect(await screen.findByRole('link', { name: 'Add a hotel' })).toHaveAttribute(
      'href',
      '/manage/hotels/new',
    );
  });

  it('explains what to do first when there are no hotels', async () => {
    mockApi({ 'GET /admin/hotels': { data: [] } });
    render();

    expect(await screen.findByRole('heading', { name: 'You have no hotels yet' })).toBeInTheDocument();
  });

  it('shows the error and retries', async () => {
    let calls = 0;
    mockApi({
      'GET /admin/hotels': () => {
        calls += 1;
        return calls === 1 ? { status: 500, error: { message: 'Something broke' } } : { data: [hotel(1)] };
      },
    });
    const user = userEvent.setup();
    render();

    expect(await screen.findByRole('heading', { name: 'We could not load your hotels' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('link', { name: 'Hotel 1' })).toBeInTheDocument();
  });
});
