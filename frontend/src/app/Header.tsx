import { Link } from 'react-router-dom';

import { useLogout } from '@/features/auth/useAuthActions';
import { useSession } from '@/shared/session/session';
import { Button } from '@/shared/ui/Button';
import { LinkButton } from '@/shared/ui/LinkButton';

export function Header() {
  const { user, isLoading } = useSession();
  const logout = useLogout();

  return (
    <header className="border-b border-line bg-white">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Link to="/" className="flex items-center gap-2 text-lg font-semibold tracking-tight text-ink">
          <img src="/favicon.svg" alt="" className="size-7" />
          AirStay
        </Link>

        <nav aria-label="Account" className="flex items-center gap-2">
          {isLoading ? null : user ? (
            <>
              {user.roles.includes('HOTEL_MANAGER') ? (
                <LinkButton to="/manage" variant="ghost" className="text-ink">
                  Manage hotels
                </LinkButton>
              ) : null}
              <LinkButton to="/account" variant="ghost" className="text-ink">
                {user.name}
              </LinkButton>
              <Button
                variant="secondary"
                loading={logout.isPending}
                onClick={() => {
                  logout.mutate();
                }}
              >
                Sign out
              </Button>
            </>
          ) : (
            <>
              <LinkButton to="/login" variant="ghost" className="text-ink">
                Sign in
              </LinkButton>
              <LinkButton to="/signup">Create account</LinkButton>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
