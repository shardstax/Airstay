import { useSearchParams } from 'react-router-dom';

import { formatDate, pluralize } from '@/shared/lib/format';
import { defaultStay, nightsBetween } from '@/shared/lib/stay';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';

import { HotelCard } from './HotelCard';
import { Pagination } from './Pagination';
import { SearchForm } from './SearchForm';
import { parseSearchParams, type SearchQuery, toSearchParams } from './searchQuery';
import { useHotelSearch } from './useHotelSearch';

export function SearchResultsPage() {
  const [params] = useSearchParams();
  const query = parseSearchParams(params);

  if (!query) {
    return (
      <div className="flex flex-col gap-6">
        <EmptyState
          title="Start a new search"
          description="This search is incomplete or its dates have passed. Choose a city and your dates below."
        />
        <SearchForm defaultValues={{ city: params.get('city') ?? '', ...defaultStay() }} />
      </div>
    );
  }
  return <Results query={query} />;
}

function Results({ query }: { query: SearchQuery }) {
  const search = useHotelSearch(query);
  const nights = nightsBetween(query.startDate, query.endDate);
  const hrefFor = (page: number) => `/search?${toSearchParams({ ...query, page }).toString()}`;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Hotels in {query.city}</h1>
        <p className="mt-1 text-ink-soft">
          {formatDate(query.startDate)} to {formatDate(query.endDate)} · {pluralize(nights, 'night')} ·{' '}
          {pluralize(query.rooms, 'room')}
        </p>
      </div>

      <SearchForm key={toSearchParams(query).toString()} defaultValues={query} />

      {search.isPending ? (
        <div role="status" aria-label="Searching hotels" className="flex flex-col gap-4">
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
          <Skeleton className="h-36" />
        </div>
      ) : search.isError ? (
        <EmptyState title="We could not load hotels" description={search.error.message}>
          <Button
            onClick={() => {
              void search.refetch();
            }}
          >
            Try again
          </Button>
        </EmptyState>
      ) : search.data.hotels.length === 0 ? (
        <EmptyState
          title="No hotels available for these dates"
          description="Try different dates, fewer rooms or another city."
        />
      ) : (
        <section
          aria-busy={search.isPlaceholderData}
          className={search.isPlaceholderData ? 'opacity-60' : undefined}
        >
          <p role="status" className="mb-4 text-sm text-ink-muted">
            {pluralize(search.data.totalElements, 'hotel')} found
          </p>
          <ul className="flex flex-col gap-4">
            {search.data.hotels.map((hotel) => (
              <li key={hotel.id} className="relative">
                <HotelCard hotel={hotel} stay={query} />
              </li>
            ))}
          </ul>
          <div className="mt-6">
            <Pagination page={query.page} totalPages={search.data.totalPages} hrefFor={hrefFor} />
          </div>
        </section>
      )}
    </div>
  );
}
