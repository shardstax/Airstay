import { NavLink, Outlet } from 'react-router-dom';

import { cn } from '@/shared/lib/cn';

const tabs = [
  { to: '/account', label: 'Profile', end: true },
  { to: '/account/bookings', label: 'My bookings', end: false },
  { to: '/account/guests', label: 'Saved guests', end: false },
] as const;

/** The frame of the account area: a title, the three sections, and the page for the one chosen. */
export function AccountLayout() {
  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">My account</h1>

      <nav aria-label="Account sections" className="mt-4 flex gap-1 border-b border-line">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              cn(
                '-mb-px border-b-2 px-3 py-2 text-sm font-medium',
                isActive
                  ? 'border-brand-600 text-brand-700'
                  : 'border-transparent text-ink-soft hover:text-ink',
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-6">
        <Outlet />
      </div>
    </div>
  );
}
