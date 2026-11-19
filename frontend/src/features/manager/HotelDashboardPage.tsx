import { Link, useParams } from 'react-router-dom';

import { ApiRequestError, describeRequestError } from '@/shared/api/errors';
import { useCatalog } from '@/shared/api/useCatalog';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { BookingsSection } from './BookingsSection';
import { ReportSection } from './ReportSection';
import { RoomsSection } from './RoomsSection';
import { useActivateHotel, useHotel } from './useHotels';
import { useRooms } from './useRooms';

export function HotelDashboardPage() {
  const { hotelId: hotelIdParam } = useParams();
  const hotelId = Number(hotelIdParam);

  if (!Number.isInteger(hotelId) || hotelId < 1) {
    return <NotFound />;
  }
  return <Dashboard hotelId={hotelId} />;
}

function NotFound() {
  return (
    <EmptyState title="Hotel not found" description="It does not exist, or it belongs to another account.">
      <LinkButton to="/manage">Back to my hotels</LinkButton>
    </EmptyState>
  );
}

function Dashboard({ hotelId }: { hotelId: number }) {
  const hotel = useHotel(hotelId);
  const catalog = useCatalog();
  const rooms = useRooms(hotelId);
  const activate = useActivateHotel(hotelId);

  if (hotel.isPending || catalog.isPending) {
    return (
      <div role="status" aria-label="Loading the hotel" className="flex flex-col gap-4">
        <Skeleton className="h-24" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (hotel.isError || catalog.isError) {
    const failure = hotel.error ?? catalog.error;
    const missing = failure instanceof ApiRequestError && [403, 404].includes(failure.status);
    return missing ? (
      <NotFound />
    ) : (
      <EmptyState title="We could not load this hotel" description={failure?.message}>
        <Button
          onClick={() => {
            void hotel.refetch();
            void catalog.refetch();
          }}
        >
          Try again
        </Button>
      </EmptyState>
    );
  }

  const live = hotel.data.active === true;
  const hasRooms = (rooms.data?.length ?? 0) > 0;

  return (
    <div className="flex flex-col gap-10">
      <header>
        <Link to="/manage" className="text-sm text-brand-700 underline underline-offset-4">
          My hotels
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight text-ink">{hotel.data.name}</h1>
            <p className="mt-1 text-ink-soft">{hotel.data.city}</p>
          </div>
          <div className="flex items-center gap-3">
            <Badge tone={live ? 'success' : 'warning'}>{live ? 'On sale' : 'Draft'}</Badge>
            <LinkButton to={`/manage/hotels/${String(hotelId)}/edit`} variant="secondary">
              Edit details
            </LinkButton>
          </div>
        </div>

        {!live ? (
          <div className="mt-6 rounded-card border border-line bg-white p-5">
            <h2 className="text-lg font-semibold text-ink">Put this hotel on sale</h2>
            <p className="mt-1 text-sm text-ink-soft">
              {hasRooms
                ? 'Guests can search for and book the hotel once it is on sale. This creates a year of nightly prices and availability for your rooms.'
                : 'Add at least one room type below, then put the hotel on sale.'}
            </p>
            {activate.isError ? (
              <p role="alert" className="mt-3 text-sm text-danger">
                {describeRequestError(activate.error)}
              </p>
            ) : null}
            <Button
              className="mt-4"
              disabled={!hasRooms}
              loading={activate.isPending}
              onClick={() => {
                activate.mutate();
              }}
            >
              Put on sale
            </Button>
          </div>
        ) : null}
      </header>

      <RoomsSection hotelId={hotelId} catalog={catalog.data} live={live} />
      {live ? <ReportSection hotelId={hotelId} /> : null}
      <BookingsSection hotelId={hotelId} />
    </div>
  );
}
