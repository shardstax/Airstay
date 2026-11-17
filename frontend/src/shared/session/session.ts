import { useQuery } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import type { components } from '@/shared/api/schema';

import { getAccessToken, hasSessionHint, setAccessToken } from './accessToken';
import { refreshAccessToken } from './refresh';

export type SessionUser = components['schemas']['UserDto'];

export const sessionQueryKey = ['session'] as const;

/**
 * Works out who is signed in: use the token already in memory, otherwise try to restore the session from the refresh cookie
 * (this is what keeps a user signed in across a page reload). Resolves to null for an anonymous visitor.
 */
export async function loadSession(): Promise<SessionUser | null> {
  const token = getAccessToken() ?? (hasSessionHint() ? await refreshAccessToken() : null);
  if (!token) {
    return null;
  }

  const { data } = await api.GET('/users/profile');
  if (!data) {
    setAccessToken(null);
    return null;
  }
  return data;
}

/** The signed-in user, or null for a visitor; `isLoading` is true only until the first answer. */
export function useSession() {
  const { data, isPending } = useQuery({
    queryKey: sessionQueryKey,
    queryFn: loadSession,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
  });
  return { user: data ?? null, isLoading: isPending };
}
