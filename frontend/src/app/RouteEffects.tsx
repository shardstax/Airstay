import { useEffect, useRef } from 'react';
import { matchPath, useLocation } from 'react-router-dom';

const SITE = 'AirStay';

/** The tab title for each address, most specific first. */
const titles: { pattern: string; title: string }[] = [
  { pattern: '/', title: 'Find a place to stay' },
  { pattern: '/search', title: 'Hotels' },
  { pattern: '/hotels/:hotelId', title: 'Hotel' },
  { pattern: '/login', title: 'Sign in' },
  { pattern: '/signup', title: 'Create your account' },
  { pattern: '/account', title: 'My account' },
  { pattern: '/account/bookings', title: 'My bookings' },
  { pattern: '/account/guests', title: 'Saved guests' },
  { pattern: '/bookings/:bookingId', title: 'Your booking' },
  { pattern: '/payments/:bookingId/status', title: 'Payment' },
  { pattern: '/manage', title: 'My hotels' },
  { pattern: '/manage/hotels/new', title: 'Add a hotel' },
  { pattern: '/manage/hotels/:hotelId', title: 'Manage hotel' },
  { pattern: '/manage/hotels/:hotelId/edit', title: 'Edit hotel' },
  { pattern: '/manage/hotels/:hotelId/rooms/:roomId/inventory', title: 'Prices and availability' },
  { pattern: '/design', title: 'Design basics' },
];

export function titleFor(pathname: string): string {
  const match = titles.find(({ pattern }) => matchPath({ path: pattern, end: true }, pathname));
  return match ? `${match.title} · ${SITE}` : `Page not found · ${SITE}`;
}

/**
 * What a single-page app must do by hand on every navigation that a normal page load does for free: set the tab title, start
 * at the top, and move focus to the page so screen-reader and keyboard users land at the new content.
 */
export function RouteEffects() {
  const { pathname } = useLocation();
  const first = useRef(true);

  useEffect(() => {
    document.title = titleFor(pathname);
    if (first.current) {
      first.current = false;
      return;
    }
    window.scrollTo(0, 0);
    document.getElementById('main')?.focus({ preventScroll: true });
  }, [pathname]);

  return null;
}
