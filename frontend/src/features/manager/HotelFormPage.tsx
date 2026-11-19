import { useNavigate, useParams } from 'react-router-dom';

import { ApiRequestError } from '@/shared/api/errors';
import { useCatalog } from '@/shared/api/useCatalog';
import { Button } from '@/shared/ui/Button';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { HotelForm } from './HotelForm';
import { useHotel } from './useHotels';

/** Create a hotel, or edit the one in the address (/manage/hotels/new and /manage/hotels/:hotelId/edit). */
export function HotelFormPage() {
  const { hotelId: hotelIdParam } = useParams();
  const editing = hotelIdParam !== undefined;
  const hotelId = editing ? Number(hotelIdParam) : undefined;
  const invalid = editing && (!Number.isInteger(hotelId) || (hotelId ?? 0) < 1);

  const navigate = useNavigate();
  const catalog = useCatalog();
  const hotel = useHotel(invalid ? undefined : hotelId);

  if (invalid) {
    return <NotFound />;
  }
  if (catalog.isPending || (editing && hotel.isPending)) {
    return (
      <div role="status" aria-label="Loading the form" className="mx-auto flex max-w-2xl flex-col gap-4">
        <Skeleton className="h-40" />
        <Skeleton className="h-40" />
      </div>
    );
  }
  if (catalog.isError || (editing && hotel.isError)) {
    const failure = catalog.error ?? hotel.error;
    const missing = failure instanceof ApiRequestError && [403, 404].includes(failure.status);
    return missing ? (
      <NotFound />
    ) : (
      <EmptyState title="We could not load the form" description={failure?.message}>
        <Button
          onClick={() => {
            void catalog.refetch();
            void hotel.refetch();
          }}
        >
          Try again
        </Button>
      </EmptyState>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-semibold tracking-tight text-ink">
        {editing ? 'Edit hotel' : 'Add a hotel'}
      </h1>
      <HotelForm
        catalog={catalog.data}
        initial={editing ? hotel.data : undefined}
        onSaved={(saved) => {
          void navigate(`/manage/hotels/${String(saved.id ?? hotelId)}`);
        }}
        onCancel={() => {
          void navigate(editing ? `/manage/hotels/${String(hotelId)}` : '/manage');
        }}
      />
    </div>
  );
}

function NotFound() {
  return (
    <EmptyState title="Hotel not found" description="It does not exist, or it belongs to another account.">
      <LinkButton to="/manage">Back to my hotels</LinkButton>
    </EmptyState>
  );
}
