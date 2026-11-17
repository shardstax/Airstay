import { Link } from 'react-router-dom';

import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { useMyHotels } from './useHotels';

/** The manager's hotels, live or still drafts, with a way to add another. */
export function ManageHotelsPage() {
  const hotels = useMyHotels();

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">My hotels</h1>
        <LinkButton to="/manage/hotels/new">Add a hotel</LinkButton>
      </div>

      <div className="mt-6">
        {hotels.isPending ? (
          <div role="status" aria-label="Loading your hotels" className="flex flex-col gap-4">
            <Skeleton className="h-24" />
            <Skeleton className="h-24" />
          </div>
        ) : hotels.isError ? (
          <EmptyState title="We could not load your hotels" description={hotels.error.message}>
            <Button
              onClick={() => {
                void hotels.refetch();
              }}
            >
              Try again
            </Button>
          </EmptyState>
        ) : hotels.data.length === 0 ? (
          <EmptyState
            title="You have no hotels yet"
            description="Add your first hotel, give it rooms, then put it on sale."
          >
            <LinkButton to="/manage/hotels/new">Add a hotel</LinkButton>
          </EmptyState>
        ) : (
          <ul className="flex flex-col gap-4">
            {hotels.data.map((hotel) => (
              <li key={hotel.id}>
                <Card className="flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <h2 className="text-lg font-semibold text-ink">
                      <Link to={`/manage/hotels/${String(hotel.id)}`} className="hover:underline">
                        {hotel.name}
                      </Link>
                    </h2>
                    <p className="text-sm text-ink-muted">{hotel.city}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <Badge tone={hotel.active ? 'success' : 'warning'}>
                      {hotel.active ? 'On sale' : 'Draft'}
                    </Badge>
                    <LinkButton to={`/manage/hotels/${String(hotel.id)}`} variant="secondary">
                      Manage
                    </LinkButton>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
