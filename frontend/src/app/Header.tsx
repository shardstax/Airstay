import { Link } from 'react-router-dom';

import { useLogout } from '@/features/auth/useAuthActions';
import { useSession } from '@/shared/session/session';
import { Button } from '@/shared/ui/Button';

const linkClass = 'inline-flex h-10 items-center rounded-lg px-4 text-sm font-medium';

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
              <Link to="/account" className={`${linkClass} text-ink hover:bg-canvas`}>
                {user.name ?? 'My account'}
              </Link>
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
              <Link to="/login" className={`${linkClass} text-ink hover:bg-canvas`}>
                Sign in
              </Link>
              <Link to="/signup" className={`${linkClass} bg-brand-600 text-white hover:bg-brand-700`}>
                Create account
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
