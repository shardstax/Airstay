import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { ApiRequestError, describeRequestError } from '@/shared/api/errors';
import { formatDate, pluralize } from '@/shared/lib/format';
import { nightsBetween, parseStay, type Stay, stayToParams } from '@/shared/lib/stay';
import { useSession } from '@/shared/session/session';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { RoomOfferCard } from './RoomOfferCard';
import { useHotelInfo } from './useHotelInfo';
import { useReserveRoom } from './useReserveRoom';

export function HotelPage() {
  const { hotelId: hotelIdParam } = useParams();
  const [params] = useSearchParams();
  const hotelId = Number(hotelIdParam);
  const stay = parseStay(params);

  if (!Number.isInteger(hotelId) || hotelId < 1) {
    return (
      <EmptyState title="Hotel not found" description="This link does not point to a hotel.">
        <HomeLink />
      </EmptyState>
    );
  }
  if (!stay) {
    return (
      <EmptyState
        title="Choose your dates"
        description="Room availability and prices depend on the dates of your stay. Search again to see them."
      >
        <HomeLink />
      </EmptyState>
    );
  }
  return <HotelDetails hotelId={hotelId} stay={stay} />;
}

function HomeLink() {
  return <LinkButton to="/">Search hotels</LinkButton>;
}

function HotelDetails({ hotelId, stay }: { hotelId: number; stay: Stay }) {
  const info = useHotelInfo(hotelId, stay);
  const reserve = useReserveRoom();
  const { user } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const nights = nightsBetween(stay.startDate, stay.endDate);

  // Holding a room needs an account: send visitors to sign in and bring them back to this page afterwards.
  const reserveRoom = (roomId: number) => {
    if (!user) {
      void navigate('/login', { state: { from: `${location.pathname}${location.search}` } });
      return;
    }
    reserve.mutate({ hotelId, roomId, stay });
  };

  if (info.isPending) {
    return (
      <div role="status" aria-label="Loading hotel" className="flex flex-col gap-4">
        <Skeleton className="h-64" />
        <Skeleton className="h-8 w-1/2" />
        <Skeleton className="h-28" />
      </div>
    );
  }

  if (info.isError) {
    const notFound = info.error instanceof ApiRequestError && info.error.status === 404;
    return (
      <EmptyState
        title={notFound ? 'Hotel not found' : 'We could not load this hotel'}
        description={notFound ? 'It may no longer be available.' : info.error.message}
      >
        {notFound ? (
          <HomeLink />
        ) : (
          <Button
            onClick={() => {
              void info.refetch();
            }}
          >
            Try again
          </Button>
        )}
      </EmptyState>
    );
  }

  const { hotel, rooms } = info.data;
  const photos = hotel.photos ?? [];
  const amenities = hotel.amenities ?? [];
  const address = hotel.contactInfo?.address;
  // The server always sends the city (the place label), but its type is optional as the field is read-only in requests.
  const resultParams = stayToParams(stay);
  if (hotel.city) resultParams.set('city', hotel.city);
  for (const amenity of new URLSearchParams(location.search).getAll('amenities')) {
    resultParams.append('amenities', amenity);
  }
  const backToResults = `/search?${resultParams.toString()}`;

  return (
    <article className="flex flex-col gap-8">
      <div>
        <Link to={backToResults} className="text-sm text-brand-700 underline underline-offset-4">
          Back to results
        </Link>
        {photos.length ? (
          <div className="mt-4 flex snap-x gap-3 overflow-x-auto rounded-2xl">
            {photos.map((photo, index) => (
              <img
                key={`${photo}-${String(index)}`}
                src={photo}
                alt={`${hotel.name}, ${String(index + 1)} of ${String(photos.length)}`}
                loading={index === 0 ? 'eager' : 'lazy'}
                className={`h-64 shrink-0 snap-start rounded-2xl object-cover sm:h-80 ${photos.length > 1 ? 'w-4/5' : 'w-full'}`}
              />
            ))}
          </div>
        ) : null}
        <h1 className="mt-4 text-3xl font-semibold tracking-tight text-ink">{hotel.name}</h1>
        <p className="mt-1 text-ink-soft">{address ? `${address}, ${hotel.city}` : hotel.city}</p>
        {amenities.length > 0 ? (
          <ul aria-label="Amenities" className="mt-4 flex flex-wrap gap-2">
            {amenities.map((amenity) => (
              <li key={amenity}>
                <Badge tone="brand">{amenity}</Badge>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

      <section aria-labelledby="rooms-heading">
        <h2 id="rooms-heading" className="text-xl font-semibold text-ink">
          Rooms for your stay
        </h2>
        <p className="mt-1 text-ink-soft">
          {formatDate(stay.startDate)} to {formatDate(stay.endDate)} · {pluralize(nights, 'night')} ·{' '}
          {pluralize(stay.rooms, 'room')}
        </p>

        {reserve.isError ? (
          <p role="alert" className="mt-4 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
            {describeRequestError(reserve.error)}
          </p>
        ) : null}

        {rooms.length === 0 ? (
          <div className="mt-4">
            <EmptyState
              title="No rooms available for these dates"
              description="Every room type is taken on at least one of these nights. Try different dates or fewer rooms."
            >
              <LinkButton to={backToResults} variant="secondary">
                Change your search
              </LinkButton>
            </EmptyState>
          </div>
        ) : (
          <ul className="mt-4 flex flex-col gap-4">
            {rooms.map((room) => (
              <li key={room.id}>
                <RoomOfferCard
                  room={room}
                  nights={nights}
                  rooms={stay.rooms}
                  busy={reserve.isPending && reserve.variables.roomId === room.id}
                  onReserve={() => {
                    reserveRoom(room.id);
                  }}
                />
              </li>
            ))}
          </ul>
        )}
      </section>
    </article>
  );
}
