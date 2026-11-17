import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { GuestsPage } from './GuestsPage';

const ada = { id: 1, name: 'Ada Lovelace', gender: 'FEMALE', dateOfBirth: '1815-12-10' };
const grace = { id: 2, name: 'Grace Hopper' };

const render = () => renderRoute(<GuestsPage />);

describe('saved guests', () => {
  it('lists the saved guests with the details they have', async () => {
    mockApi({ 'GET /users/guests': { data: [ada, grace] } });
    render();

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('Female · born 10 Dec 1815')).toBeInTheDocument();
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.getByText('No other details')).toBeInTheDocument();
  });

  it('says so when there are none', async () => {
    mockApi({ 'GET /users/guests': { data: [] } });
    render();

    expect(await screen.findByRole('heading', { name: 'No saved guests' })).toBeInTheDocument();
  });

  it('adds a guest and shows them in the list', async () => {
    let saved: unknown[] = [ada];
    const backend = mockApi({
      'GET /users/guests': () => ({ data: saved }),
      'POST /users/guests': () => {
        saved = [...saved, grace];
        return { status: 201, data: grace };
      },
    });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Add a guest' }));
    await user.type(screen.getByLabelText('Full name'), 'Grace Hopper');
    await user.click(screen.getByRole('button', { name: 'Save guest' }));

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'POST /users/guests')?.body).toEqual({
      name: 'Grace Hopper',
    });
    expect(screen.queryByRole('form', { name: 'Add a guest' })).not.toBeInTheDocument();
  });

  it('edits a guest, starting from what is saved', async () => {
    const backend = mockApi({
      'GET /users/guests': { data: [ada] },
      'PUT /users/guests/1': { status: 204 },
    });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Edit Ada Lovelace' }));
    const form = screen.getByRole('form', { name: 'Edit guest' });
    expect(within(form).getByLabelText('Full name')).toHaveValue('Ada Lovelace');
    expect(within(form).getByLabelText('Date of birth (optional)')).toHaveValue('1815-12-10');

    await user.clear(within(form).getByLabelText('Full name'));
    await user.type(within(form).getByLabelText('Full name'), 'Augusta Ada King');
    await user.click(within(form).getByRole('button', { name: 'Save changes' }));

    await vi.waitFor(() => {
      expect(screen.queryByRole('form', { name: 'Edit guest' })).not.toBeInTheDocument();
    });
    expect(backend.calls.find((call) => call.key === 'PUT /users/guests/1')?.body).toEqual({
      name: 'Augusta Ada King',
      gender: 'FEMALE',
      dateOfBirth: '1815-12-10',
    });
  });

  it('asks before deleting, and deletes on confirmation', async () => {
    let saved: unknown[] = [ada, grace];
    const backend = mockApi({
      'GET /users/guests': () => ({ data: saved }),
      'DELETE /users/guests/2': () => {
        saved = [ada];
        return { status: 204 };
      },
    });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Delete Grace Hopper' }));
    expect(screen.getByRole('group', { name: 'Confirm deleting Grace Hopper' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }));

    await vi.waitFor(() => {
      expect(screen.queryByText('Grace Hopper')).not.toBeInTheDocument();
    });
    expect(backend.count('DELETE /users/guests/2')).toBe(1);
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument();
  });

  it('keeps a guest when the deletion is called off', async () => {
    const backend = mockApi({ 'GET /users/guests': { data: [grace] } });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Delete Grace Hopper' }));
    await user.click(screen.getByRole('button', { name: 'Keep' }));

    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(backend.count('DELETE /users/guests/2')).toBe(0);
  });

  it('shows the server’s reason when a deletion is refused', async () => {
    mockApi({
      'GET /users/guests': { data: [grace] },
      'DELETE /users/guests/2': { status: 409, error: { message: 'This guest is on a booking' } },
    });
    const user = userEvent.setup();
    render();

    await user.click(await screen.findByRole('button', { name: 'Delete Grace Hopper' }));
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('This guest is on a booking');
  });

  it('shows the error and retries', async () => {
    let calls = 0;
    mockApi({
      'GET /users/guests': () => {
        calls += 1;
        return calls === 1 ? { status: 500, error: { message: 'Something broke' } } : { data: [ada] };
      },
    });
    const user = userEvent.setup();
    render();

    expect(await screen.findByRole('heading', { name: 'We could not load your guests' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
  });
});
