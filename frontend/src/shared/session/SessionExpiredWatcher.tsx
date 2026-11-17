import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { onSessionExpired } from './accessToken';
import { sessionQueryKey } from './session';

/**
 * Listens for the moment the session can no longer be renewed (the refresh cookie expired or was revoked while the page
 * was open), clears the signed-in state and sends the user to sign in again, returning to where they were.
 */
export function SessionExpiredWatcher() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(
    () =>
      onSessionExpired(() => {
        queryClient.setQueryData(sessionQueryKey, null);
        void navigate('/login', {
          replace: true,
          state: { reason: 'expired', from: `${location.pathname}${location.search}` },
        });
      }),
    [queryClient, navigate, location.pathname, location.search],
  );

  return null;
}
