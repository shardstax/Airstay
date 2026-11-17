import { Outlet } from 'react-router-dom';

import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';

import { useSession } from './session';

/** Route wrapper for the hotel manager area, placed inside RequireAuth. Other accounts are told, not silently redirected. */
export function RequireManager() {
  const { user } = useSession();

  if (!user?.roles.includes('HOTEL_MANAGER')) {
    return (
      <EmptyState
        title="This area is for hotel managers"
        description="Your account is a guest account. To list hotels, create an account as a hotel manager."
      >
        <LinkButton to="/account" variant="secondary">
          Go to my account
        </LinkButton>
      </EmptyState>
    );
  }
  return <Outlet />;
}
