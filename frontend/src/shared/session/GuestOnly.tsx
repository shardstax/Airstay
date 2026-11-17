import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { safeRedirectPath } from '@/shared/lib/safeRedirect';
import { Spinner } from '@/shared/ui/Spinner';

import { useSession } from './session';

interface RedirectState {
  from?: unknown;
}

/**
 * Route wrapper for the sign-in and sign-up pages. A signed-in user has no business there, so they go on to the page they
 * were heading for (or the start page). This is also what moves a user along right after a successful login.
 */
export function GuestOnly() {
  const { user, isLoading } = useSession();
  const location = useLocation();

  if (isLoading) {
    return <Spinner label="Checking your session" />;
  }
  if (user) {
    const state = location.state as RedirectState | null;
    return <Navigate to={safeRedirectPath(state?.from)} replace />;
  }
  return <Outlet />;
}
