import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter } from 'react-router-dom';

import { RouteEffects, titleFor } from './RouteEffects';

describe('titleFor', () => {
  it.each([
    ['/', 'Find a place to stay · AirStay'],
    ['/search', 'Hotels · AirStay'],
    ['/hotels/7', 'Hotel · AirStay'],
    ['/login', 'Sign in · AirStay'],
    ['/account', 'My account · AirStay'],
    ['/account/bookings', 'My bookings · AirStay'],
    ['/bookings/42', 'Your booking · AirStay'],
    ['/payments/42/status', 'Payment · AirStay'],
    ['/manage', 'My hotels · AirStay'],
    ['/manage/hotels/new', 'Add a hotel · AirStay'],
    ['/manage/hotels/9', 'Manage hotel · AirStay'],
    ['/manage/hotels/9/edit', 'Edit hotel · AirStay'],
    ['/manage/hotels/9/rooms/3/inventory', 'Prices and availability · AirStay'],
  ])('%s is titled "%s"', (path, title) => {
    expect(titleFor(path)).toBe(title);
  });

  it('says so for an address that does not exist', () => {
    expect(titleFor('/nowhere')).toBe('Page not found · AirStay');
  });
});

describe('RouteEffects', () => {
  function Page() {
    return (
      <MemoryRouter initialEntries={['/']}>
        <RouteEffects />
        <main id="main" tabIndex={-1}>
          <Link to="/login">Go to sign in</Link>
        </main>
      </MemoryRouter>
    );
  }

  it('sets the tab title on the first page', () => {
    render(<Page />);

    expect(document.title).toBe('Find a place to stay · AirStay');
  });

  it('changes the title, scrolls to the top and moves focus to the page on navigation', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    const user = userEvent.setup();
    render(<Page />);

    await user.click(screen.getByRole('link', { name: 'Go to sign in' }));

    expect(document.title).toBe('Sign in · AirStay');
    expect(scrollTo).toHaveBeenCalledWith(0, 0);
    expect(screen.getByRole('main')).toHaveFocus();
  });
});
