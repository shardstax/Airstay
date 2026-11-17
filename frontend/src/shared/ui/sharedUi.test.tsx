import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { Route, Routes } from 'react-router-dom';

import { RequireManager } from '@/shared/session/RequireManager';
import { ada, mockApi, rememberPreviousSession } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { CheckboxGroup } from './CheckboxGroup';
import { ErrorBoundary } from './ErrorBoundary';

describe('CheckboxGroup', () => {
  function Harness({ initial = [] as string[] }) {
    const [value, setValue] = useState(initial);
    return (
      <>
        <CheckboxGroup
          legend="Amenities"
          options={['WiFi', 'Pool', 'Spa']}
          value={value}
          onChange={setValue}
        />
        <p data-testid="chosen">{value.join(',')}</p>
      </>
    );
  }

  it('lists every option under its legend', () => {
    render(<Harness />);

    expect(screen.getByRole('group', { name: 'Amenities' })).toBeInTheDocument();
    expect(screen.getAllByRole('checkbox')).toHaveLength(3);
  });

  it('adds and removes choices', async () => {
    const user = userEvent.setup();
    render(<Harness initial={['Pool']} />);

    await user.click(screen.getByLabelText('WiFi'));
    expect(screen.getByTestId('chosen')).toHaveTextContent('Pool,WiFi');

    await user.click(screen.getByLabelText('Pool'));
    expect(screen.getByTestId('chosen')).toHaveTextContent('WiFi');
  });

  it('shows what is already chosen', () => {
    render(<Harness initial={['Spa']} />);

    expect(screen.getByLabelText('Spa')).toBeChecked();
    expect(screen.getByLabelText('WiFi')).not.toBeChecked();
  });

  it('announces an error and ties it to the group', () => {
    render(
      <CheckboxGroup
        legend="Amenities"
        options={['WiFi']}
        value={[]}
        onChange={() => undefined}
        error="Choose from the list."
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('Choose from the list.');
    expect(screen.getByRole('group', { name: 'Amenities' })).toHaveAccessibleDescription(
      'Choose from the list.',
    );
  });
});

describe('ErrorBoundary', () => {
  function Broken(): never {
    throw new Error('boom');
  }

  it('shows a plain message instead of a blank page when a screen throws', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the start' })).toHaveAttribute('href', '/');
  });

  it('renders its children when nothing is wrong', () => {
    render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>,
    );

    expect(screen.getByText('All good')).toBeInTheDocument();
  });
});

describe('RequireManager', () => {
  function renderManagerArea() {
    return renderRoute(
      <Routes>
        <Route element={<RequireManager />}>
          <Route path="/manage" element={<p>Manager area</p>} />
        </Route>
      </Routes>,
      { route: '/manage' },
    );
  }
  const signedInAs = (roles: string[]) => ({
    'POST /auth/refresh': { data: { accessToken: 'token' } },
    'GET /users/profile': { data: { ...ada, roles } },
  });

  it('lets a hotel manager in', async () => {
    rememberPreviousSession();
    mockApi(signedInAs(['GUEST', 'HOTEL_MANAGER']));
    renderManagerArea();

    expect(await screen.findByText('Manager area')).toBeInTheDocument();
  });

  it('tells a guest account that the area is for hotel managers', async () => {
    rememberPreviousSession();
    mockApi(signedInAs(['GUEST']));
    renderManagerArea();

    expect(
      await screen.findByRole('heading', { name: 'This area is for hotel managers' }),
    ).toBeInTheDocument();
    expect(screen.queryByText('Manager area')).not.toBeInTheDocument();
  });
});
