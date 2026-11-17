import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { Spinner } from '@/shared/ui/Spinner';

import { useSession } from './session';

/** Route wrapper for pages that need a signed-in user. Sends visitors to /login and brings them back afterwards. */
export function RequireAuth() {
  const { user, isLoading } = useSession();
  const location = useLocation();

  if (isLoading) {
    return <Spinner label="Checking your session" />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  return <Outlet />;
}
