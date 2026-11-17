import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ada, mockApi, rememberPreviousSession } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { ProfilePage } from './ProfilePage';

const signedIn = {
  'POST /auth/refresh': { data: { accessToken: 'token' } },
  'GET /users/profile': {
    data: { ...ada, gender: 'FEMALE', dateOfBirth: '1990-04-02', roles: ['GUEST', 'HOTEL_MANAGER'] },
  },
};

function renderProfile() {
  rememberPreviousSession();
  return renderRoute(<ProfilePage />);
}

describe('the profile', () => {
  it('shows the email and account type, and the saved details in the form', async () => {
    mockApi(signedIn);
    renderProfile();

    expect(await screen.findByText('ada@example.com')).toBeInTheDocument();
    expect(screen.getByText('Guest')).toBeInTheDocument();
    expect(screen.getByText('Hotel manager')).toBeInTheDocument();
    expect(screen.getByLabelText('Full name')).toHaveValue('Ada Lovelace');
    expect(screen.getByLabelText('Gender (optional)')).toHaveValue('FEMALE');
    expect(screen.getByLabelText('Date of birth (optional)')).toHaveValue('1990-04-02');
  });

  it('saves the changed details', async () => {
    const backend = mockApi({ ...signedIn, 'PATCH /users/profile': { status: 204 } });
    const user = userEvent.setup();
    renderProfile();

    const name = await screen.findByLabelText('Full name');
    await user.clear(name);
    await user.type(name, 'Ada King');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Your details were saved.')).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'PATCH /users/profile')?.body).toEqual({
      name: 'Ada King',
      gender: 'FEMALE',
      dateOfBirth: '1990-04-02',
    });
  });

  it('reloads the profile after saving, so the new name shows up', async () => {
    const backend = mockApi({ ...signedIn, 'PATCH /users/profile': { status: 204 } });
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save changes' }));
    await screen.findByText('Your details were saved.');

    expect(backend.count('GET /users/profile')).toBe(2);
  });

  it('asks for a name', async () => {
    const backend = mockApi({ ...signedIn, 'PATCH /users/profile': { status: 204 } });
    const user = userEvent.setup();
    renderProfile();

    await user.clear(await screen.findByLabelText('Full name'));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Enter your name.')).toBeInTheDocument();
    expect(backend.count('PATCH /users/profile')).toBe(0);
  });

  it('shows the server’s reason when the save is refused', async () => {
    mockApi({
      ...signedIn,
      'PATCH /users/profile': { status: 400, error: { message: 'Validation failed' } },
    });
    const user = userEvent.setup();
    renderProfile();

    await user.click(await screen.findByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Validation failed');
  });
});
