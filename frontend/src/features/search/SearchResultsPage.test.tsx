import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';

import { addDays, today } from '@/shared/lib/stay';
import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { HomePage } from './HomePage';
import { SearchResultsPage } from './SearchResultsPage';

const startDate = addDays(today(), 30);
const endDate = addDays(startDate, 2);
const searchUrl = (extra = '') =>
  `/search?city=Pune&startDate=${startDate}&endDate=${endDate}&rooms=1${extra}`;

const hotel = (id: number, overrides: Record<string, unknown> = {}) => ({
  id,
  name: `Hotel ${String(id)}`,
  city: 'Pune',
  price: 2500,
  photos: [],
  amenities: ['WiFi', 'Pool'],
  ...overrides,
});

const page = (hotels: unknown[], totalPages = 1, totalElements = hotels.length) => ({
  content: hotels,
  page: { size: 10, number: 0, totalElements, totalPages },
});

/** Shows where the router is, so a test can check what a form navigated to. */
function Where() {
  const location = useLocation();
  return <p data-testid="where">{`${location.pathname}${location.search}`}</p>;
}

function renderSearch(route: string) {
  return renderRoute(
    <>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/search" element={<SearchResultsPage />} />
      </Routes>
      <Where />
    </>,
    { route },
  );
}

describe('the search results', () => {
  it('lists hotels with the price of the cheapest room per night', async () => {
    mockApi({
      'GET /hotels/search': {
        data: page([hotel(7, { price: 2499.6 }), hotel(8, { name: 'Lakeview', price: 3100 })]),
      },
    });
    renderSearch(searchUrl());

    expect(await screen.findByRole('link', { name: 'Hotel 7' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Lakeview' })).toBeInTheDocument();
    expect(screen.getByText('₹2,500', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('₹3,100', { exact: false })).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '' })).toHaveTextContent('2 hotels found');
  });

  it('asks the API for exactly what the URL says, with a zero-based page', async () => {
    const backend = mockApi({ 'GET /hotels/search': { data: page([hotel(7)]) } });
    renderSearch(searchUrl('&page=1'));

    await screen.findByRole('link', { name: 'Hotel 7' });
    const query = backend.calls[0]?.query;
    expect(Object.fromEntries(query ?? [])).toEqual({
      city: 'Pune',
      startDate,
      endDate,
      roomsCount: '1',
      page: '0',
      size: '10',
    });
  });

  it('links each hotel to its page, carrying the dates and rooms along', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)]) } });
    renderSearch(searchUrl());

    const link = await screen.findByRole('link', { name: 'Hotel 7' });

    expect(link).toHaveAttribute('href', `/hotels/7?startDate=${startDate}&endDate=${endDate}&rooms=1`);
  });

  it('summarises the stay', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)]) } });
    renderSearch(searchUrl());

    expect(await screen.findByRole('heading', { name: 'Hotels in Pune' })).toBeInTheDocument();
    expect(screen.getByText(/2 nights · 1 room/)).toBeInTheDocument();
  });

  it('shows at most four amenities and counts the rest', async () => {
    mockApi({
      'GET /hotels/search': {
        data: page([hotel(7, { amenities: ['WiFi', 'Pool', 'Gym', 'Spa', 'Bar', 'Parking'] })]),
      },
    });
    renderSearch(searchUrl());

    expect(await screen.findByText('+2 more')).toBeInTheDocument();
    expect(screen.queryByText('Bar')).not.toBeInTheDocument();
  });

  it('copes with a hotel that has no photos or amenities at all', async () => {
    mockApi({
      'GET /hotels/search': { data: page([{ id: 9, name: 'Plain Inn', city: 'Pune', price: 1200 }]) },
    });
    renderSearch(searchUrl());

    expect(await screen.findByRole('link', { name: 'Plain Inn' })).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Amenities' })).not.toBeInTheDocument();
  });

  it('shows a loading state while it waits', async () => {
    let release: () => void = () => undefined;
    mockApi({
      'GET /hotels/search': () =>
        new Promise((resolve) => {
          release = () => {
            resolve({ data: page([hotel(7)]) });
          };
        }),
    });
    renderSearch(searchUrl());

    expect(await screen.findByRole('status', { name: 'Searching hotels' })).toBeInTheDocument();
    release();
    expect(await screen.findByRole('link', { name: 'Hotel 7' })).toBeInTheDocument();
    expect(screen.queryByRole('status', { name: 'Searching hotels' })).not.toBeInTheDocument();
  });

  it('says so when nothing is available', async () => {
    mockApi({ 'GET /hotels/search': { data: page([], 0, 0) } });
    renderSearch(searchUrl());

    expect(
      await screen.findByRole('heading', { name: 'No hotels available for these dates' }),
    ).toBeInTheDocument();
  });

  it('shows the error and lets the user try again', async () => {
    let calls = 0;
    mockApi({
      'GET /hotels/search': () => {
        calls += 1;
        return calls === 1
          ? { status: 500, error: { message: 'Search is down' } }
          : { data: page([hotel(7)]) };
      },
    });
    const user = userEvent.setup();
    renderSearch(searchUrl());

    expect(await screen.findByRole('heading', { name: 'We could not load hotels' })).toBeInTheDocument();
    expect(screen.getByText('Search is down')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('link', { name: 'Hotel 7' })).toBeInTheDocument();
  });

  it('asks for a new search when the URL is incomplete', () => {
    const backend = mockApi({});
    renderSearch(`/search?city=Pune`);

    expect(screen.getByRole('heading', { name: 'Start a new search' })).toBeInTheDocument();
    expect(screen.getByLabelText('City')).toHaveValue('Pune');
    expect(backend.calls).toHaveLength(0);
  });

  it('asks for a new search when the dates have already passed', () => {
    mockApi({});
    renderSearch('/search?city=Pune&startDate=2020-01-01&endDate=2020-01-03');

    expect(screen.getByRole('heading', { name: 'Start a new search' })).toBeInTheDocument();
  });
});

describe('paging through results', () => {
  it('offers Next but not Previous on the first page', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)], 3, 25) } });
    renderSearch(searchUrl());

    const next = await screen.findByRole('link', { name: 'Next' });

    expect(next).toHaveAttribute('href', expect.stringContaining('page=2'));
    expect(screen.queryByRole('link', { name: 'Previous' })).not.toBeInTheDocument();
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument();
  });

  it('offers both on a middle page, and Previous returns to the first without a page parameter', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)], 3, 25) } });
    renderSearch(searchUrl('&page=2'));

    const previous = await screen.findByRole('link', { name: 'Previous' });

    expect(previous.getAttribute('href')).not.toContain('page=');
    expect(screen.getByRole('link', { name: 'Next' })).toHaveAttribute(
      'href',
      expect.stringContaining('page=3'),
    );
  });

  it('offers only Previous on the last page', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)], 3, 25) } });
    renderSearch(searchUrl('&page=3'));

    await screen.findByRole('link', { name: 'Previous' });

    expect(screen.queryByRole('link', { name: 'Next' })).not.toBeInTheDocument();
  });

  it('shows no pager when everything fits on one page', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)]) } });
    renderSearch(searchUrl());

    await screen.findByRole('link', { name: 'Hotel 7' });

    expect(screen.queryByRole('navigation', { name: 'Results pages' })).not.toBeInTheDocument();
  });

  it('loads the next page when Next is followed', async () => {
    const backend = mockApi({ 'GET /hotels/search': { data: page([hotel(7)], 2, 12) } });
    const user = userEvent.setup();
    renderSearch(searchUrl());

    await user.click(await screen.findByRole('link', { name: 'Next' }));

    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument();
    expect(backend.calls.at(-1)?.query.get('page')).toBe('1');
  });
});

describe('the search form', () => {
  const fillCity = async (user: ReturnType<typeof userEvent.setup>, city: string) => {
    await user.clear(screen.getByLabelText('City'));
    if (city) {
      await user.type(screen.getByLabelText('City'), city);
    }
  };

  it('asks for a city before searching', async () => {
    const user = userEvent.setup();
    renderSearch('/');

    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Choose a place from the list.')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/');
  });

  it('starts from tomorrow for two nights in one room', () => {
    renderSearch('/');

    expect(screen.getByLabelText('Check-in')).toHaveValue(addDays(today(), 1));
    expect(screen.getByLabelText('Check-out')).toHaveValue(addDays(today(), 3));
    expect(screen.getByLabelText('Rooms')).toHaveValue('1');
  });

  it('refuses a check-out that is not after check-in', async () => {
    const user = userEvent.setup();
    renderSearch('/');

    await fillCity(user, 'Pune');
    fireEvent.change(screen.getByLabelText('Check-out'), { target: { value: addDays(today(), 1) } });
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Check-out must be after check-in.')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/');
  });

  it('refuses a check-in in the past', async () => {
    const user = userEvent.setup();
    renderSearch('/');

    await fillCity(user, 'Pune');
    fireEvent.change(screen.getByLabelText('Check-in'), { target: { value: addDays(today(), -1) } });
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('Check-in cannot be in the past.')).toBeInTheDocument();
  });

  it('takes the user to the results with the search in the URL', async () => {
    mockApi({
      'GET /hotels/search': { data: page([hotel(7)]) },
      'GET /places': {
        data: [
          {
            id: 1272596,
            name: 'Panaji',
            region: 'Goa',
            country: 'India',
            countryCode: 'IN',
            latitude: 15,
            longitude: 73,
            label: 'Panaji, Goa, India',
          },
        ],
      },
    });
    const user = userEvent.setup();
    renderSearch('/');

    await fillCity(user, 'Pan');
    await user.click(await screen.findByRole('option', { name: 'Panaji, Goa, India' }));
    await user.selectOptions(screen.getByLabelText('Rooms'), '3');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    // The location display exists from the start (showing '/'), so wait until it has actually moved.
    await vi.waitFor(() => {
      expect(screen.getByTestId('where')).toHaveTextContent('/search?');
    });
    const where = screen.getByTestId('where');
    const params = new URLSearchParams(where.textContent.split('?')[1]);
    expect(params.get('city')).toBe('Panaji, Goa, India');
    expect(params.get('rooms')).toBe('3');
    expect(params.get('startDate')).toBe(addDays(today(), 1));
  });

  it('keeps the current search filled in on the results page, ready to change', async () => {
    mockApi({ 'GET /hotels/search': { data: page([hotel(7)]) } });
    renderSearch(searchUrl());

    await screen.findByRole('link', { name: 'Hotel 7' });

    expect(screen.getByLabelText('City')).toHaveValue('Pune');
    expect(screen.getByLabelText('Check-in')).toHaveValue(startDate);
    expect(screen.getByLabelText('Check-out')).toHaveValue(endDate);
  });
});
