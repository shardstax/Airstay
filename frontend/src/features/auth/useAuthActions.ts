import { useMutation, useQueryClient } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import { setAccessToken } from '@/shared/session/accessToken';
import { sessionQueryKey, type SessionUser } from '@/shared/session/session';

import type { LoginValues, SignupValues } from './schemas';

async function fetchProfile(): Promise<SessionUser> {
  const { data, error, response } = await api.GET('/users/profile');
  if (!data) {
    throw toRequestError(response, error);
  }
  return data;
}

async function login(values: LoginValues): Promise<SessionUser> {
  const { data, error, response } = await api.POST('/auth/login', { body: values });
  if (!data?.accessToken) {
    throw toRequestError(response, error);
  }
  setAccessToken(data.accessToken);
  return fetchProfile();
}

async function signup(values: SignupValues): Promise<SessionUser> {
  const { manager, ...account } = values;
  const { error, response } = await api.POST('/auth/signup', {
    body: { ...account, ...(manager ? { accountType: 'HOTEL_MANAGER' as const } : {}) },
  });
  if (!response.ok) {
    throw toRequestError(response, error);
  }
  // A new account is signed in straight away, so there is no second form to fill in.
  return login({ email: values.email, password: values.password });
}

async function logout(): Promise<void> {
  try {
    // Asks the server to clear the refresh cookie. Whether or not that works, this browser forgets the session below.
    await api.POST('/auth/logout');
  } finally {
    setAccessToken(null);
  }
}

/** Signs in. On success the session is stored, and the route guards move the user on. */
export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: login,
    onSuccess: (user) => {
      queryClient.setQueryData(sessionQueryKey, user);
    },
  });
}

/** Creates an account and signs it in. */
export function useSignup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: signup,
    onSuccess: (user) => {
      queryClient.setQueryData(sessionQueryKey, user);
    },
  });
}

/** Signs out and drops everything cached for the previous user. */
export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSettled: () => {
      // Not `clear()`: that would also delete the session query the header and route guards are watching, and they would
      // never hear that the user is gone.
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionQueryKey[0] });
      queryClient.setQueryData(sessionQueryKey, null);
    },
  });
}
