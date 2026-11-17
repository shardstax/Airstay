import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { hasSessionHint, notifySessionExpired } from '@/shared/session/accessToken';
import { ada, mockApi, rememberPreviousSession } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { App } from './App';

const noSession = {
  'POST /auth/refresh': { status: 401, error: { message: 'Refresh token cookie is missing' } },
};
const restorableSession = {
  'POST /auth/refresh': { data: { accessToken: 'restored' } },
  'GET /users/profile': { data: ada },
};
const health = { 'GET /actuator/health': { raw: { status: 'UP' } } };
const loginRoutes = {
  'POST /auth/login': { data: { accessToken: 'new-token' } },
  'GET /users/profile': { data: ada },
};

async function fillAndSubmitLogin(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email'), 'ada@example.com');
  await user.type(screen.getByLabelText('Password'), 'correct horse');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('the app shell', () => {
  it('has a skip link and a main landmark', async () => {
    mockApi({ ...noSession, 'GET /actuator/health': { raw: { status: 'UP' } } });
    renderRoute(<App />);

    expect(screen.getByRole('link', { name: /skip to content/i })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toBeInTheDocument();
    await screen.findByRole('link', { name: 'Sign in' });
  });

  it('shows a not-found page for an unknown route', async () => {
    mockApi(noSession);
    renderRoute(<App />, { route: '/nope' });

    expect(await screen.findByRole('heading', { name: /page not found/i })).toBeInTheDocument();
  });
});

describe('the home page API check', () => {
  it('shows Connected when the health endpoint answers UP', async () => {
    mockApi({ ...noSession, 'GET /actuator/health': { raw: { status: 'UP' } } });
    renderRoute(<App />);

    expect(await screen.findByText('Connected')).toBeInTheDocument();
  });

  it('shows Unreachable when the health endpoint is not UP', async () => {
    mockApi({ ...noSession, 'GET /actuator/health': { status: 503, raw: { status: 'DOWN' } } });
    renderRoute(<App />);

    expect(await screen.findByText('Unreachable')).toBeInTheDocument();
  });

  it('shows Unreachable when the API cannot be reached', async () => {
    mockApi({
      ...noSession,
      'GET /actuator/health': () => {
        throw new TypeError('network down');
      },
    });
    renderRoute(<App />);

    expect(await screen.findByText('Unreachable')).toBeInTheDocument();
  });
});

describe('a visitor who is not signed in', () => {
  it('makes no refresh request, since there is no session to restore', async () => {
    const backend = mockApi({ ...noSession, 'GET /actuator/health': { raw: { status: 'UP' } } });
    renderRoute(<App />);

    await screen.findByRole('link', { name: 'Sign in' });
    expect(backend.count('POST /auth/refresh')).toBe(0);
  });

  it('is treated as signed out, and forgotten, when the remembered session is gone', async () => {
    rememberPreviousSession();
    mockApi(noSession);
    renderRoute(<App />, { route: '/design' });

    await screen.findByRole('link', { name: 'Sign in' });
    expect(hasSessionHint()).toBe(false);
  });

  it('sees Sign in and Create account in the header', async () => {
    mockApi(noSession);
    renderRoute(<App />, { route: '/design' });

    expect(await screen.findByRole('link', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Create account' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument();
  });

  it('is sent to the sign-in page when opening a protected page', async () => {
    mockApi(noSession);
    renderRoute(<App />, { route: '/account' });

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('is brought back to the protected page after signing in', async () => {
    const backend = mockApi({ ...noSession, ...loginRoutes });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/account' });

    await screen.findByRole('heading', { name: 'Sign in' });
    await fillAndSubmitLogin(user);

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(screen.getByText('ada@example.com')).toBeInTheDocument();
    expect(backend.count('POST /auth/login')).toBe(1);
  });
});

describe('signing in', () => {
  it('signs in, shows the name and uses the token for the next call', async () => {
    const backend = mockApi({ ...noSession, ...loginRoutes });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/login' });

    await screen.findByRole('heading', { name: 'Sign in' });
    await fillAndSubmitLogin(user);

    expect(await screen.findByRole('link', { name: 'Ada Lovelace' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'POST /auth/login')?.body).toEqual({
      email: 'ada@example.com',
      password: 'correct horse',
    });
    expect(backend.calls.filter((call) => call.key === 'GET /users/profile').at(-1)?.authorization).toBe(
      'Bearer new-token',
    );
  });

  it('explains a wrong password and lets the user try again', async () => {
    mockApi({ ...noSession, 'POST /auth/login': { status: 401, error: { message: 'Bad credentials' } } });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/login' });

    await screen.findByRole('heading', { name: 'Sign in' });
    await fillAndSubmitLogin(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('explains rate limiting', async () => {
    mockApi({
      ...noSession,
      'POST /auth/login': {
        status: 429,
        error: { message: 'Too many login attempts' },
        headers: { 'retry-after': '60' },
      },
    });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/login' });

    await screen.findByRole('heading', { name: 'Sign in' });
    await fillAndSubmitLogin(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(/too many attempts/i);
  });

  it('says so when the server cannot be reached', async () => {
    mockApi({
      ...noSession,
      'POST /auth/login': () => {
        throw new TypeError('Failed to fetch');
      },
    });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/login' });

    await screen.findByRole('heading', { name: 'Sign in' });
    await fillAndSubmitLogin(user);

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the server/i);
  });

  it('checks the form before sending anything', async () => {
    const backend = mockApi(noSession);
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/login' });

    await screen.findByRole('heading', { name: 'Sign in' });
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(backend.count('POST /auth/login')).toBe(0);
  });

  it('does not let a signed-in user see the sign-in page', async () => {
    rememberPreviousSession();
    mockApi(restorableSession);
    renderRoute(<App />, { route: '/login' });

    expect(await screen.findByRole('link', { name: 'Ada Lovelace' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument();
  });
});

describe('signing up', () => {
  const fillSignup = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.type(screen.getByLabelText('Full name'), 'Ada Lovelace');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'correct horse');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
  };

  it('creates the account and signs the new user in', async () => {
    const backend = mockApi({
      ...noSession,
      'POST /auth/signup': { status: 201, data: ada },
      ...loginRoutes,
      ...health,
    });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/signup' });

    await screen.findByRole('heading', { name: 'Create your account' });
    await fillSignup(user);

    expect(await screen.findByRole('link', { name: 'Ada Lovelace' })).toBeInTheDocument();
    expect(
      backend.keys().filter((key) => key !== 'POST /auth/refresh' && key !== 'GET /actuator/health'),
    ).toEqual(['POST /auth/signup', 'POST /auth/login', 'GET /users/profile']);
  });

  it('shows the server message when the email is already registered', async () => {
    mockApi({
      ...noSession,
      'POST /auth/signup': { status: 409, error: { message: 'An account with this email already exists' } },
    });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/signup' });

    await screen.findByRole('heading', { name: 'Create your account' });
    await fillSignup(user);

    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists');
  });

  it('rejects a short password before sending anything', async () => {
    const backend = mockApi(noSession);
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/signup' });

    await screen.findByRole('heading', { name: 'Create your account' });
    await user.type(screen.getByLabelText('Full name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Use at least 8 characters.')).toBeInTheDocument();
    expect(backend.count('POST /auth/signup')).toBe(0);
  });
});

describe('a returning user', () => {
  it('stays signed in after a page reload, restored from the refresh cookie', async () => {
    rememberPreviousSession();
    const backend = mockApi(restorableSession);
    renderRoute(<App />, { route: '/account' });

    expect(await screen.findByRole('heading', { name: 'My account' })).toBeInTheDocument();
    expect(screen.getByText('ada@example.com')).toBeInTheDocument();
    expect(screen.getByText('Guest')).toBeInTheDocument();
    expect(backend.calls.find((call) => call.key === 'GET /users/profile')?.authorization).toBe(
      'Bearer restored',
    );
  });

  it('signs out, forgets the user and protects the account page again', async () => {
    rememberPreviousSession();
    const backend = mockApi({ ...restorableSession, 'POST /auth/logout': { status: 204 } });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/account' });

    await screen.findByRole('heading', { name: 'My account' });
    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(backend.count('POST /auth/logout')).toBe(1);
    expect(screen.getByRole('link', { name: 'Create account' })).toBeInTheDocument();
  });

  it('signs out here even if the server cannot be told', async () => {
    rememberPreviousSession();
    mockApi({ ...restorableSession, 'POST /auth/logout': { status: 500, error: { message: 'boom' } } });
    const user = userEvent.setup();
    renderRoute(<App />, { route: '/account' });

    await screen.findByRole('heading', { name: 'My account' });
    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('is sent to sign in again, with an explanation, when the session expires', async () => {
    rememberPreviousSession();
    mockApi(restorableSession);
    renderRoute(<App />, { route: '/account' });
    await screen.findByRole('heading', { name: 'My account' });

    act(() => {
      notifySessionExpired();
    });

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByText('Your session has expired. Please sign in again.')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Create account' })).toBeInTheDocument();
    });
  });
});
