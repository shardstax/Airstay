import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';

import { describeRequestError } from '@/shared/api/errors';
import { formatDate, formatPrice } from '@/shared/lib/format';
import { addDays, today } from '@/shared/lib/stay';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Input } from '@/shared/ui/Input';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { inventorySchema, type InventoryValues, MAX_SURGE, MIN_SURGE } from './inventorySchema';
import { useInventory, useUpdateInventory } from './useHotelActivity';

const PAGE = 30;

export function InventoryPage() {
  const { hotelId: hotelIdParam, roomId: roomIdParam } = useParams();
  const hotelId = Number(hotelIdParam);
  const roomId = Number(roomIdParam);

  if (!Number.isInteger(hotelId) || !Number.isInteger(roomId) || hotelId < 1 || roomId < 1) {
    return (
      <EmptyState title="Room type not found" description="This link does not point to a room type.">
        <LinkButton to="/manage">Back to my hotels</LinkButton>
      </EmptyState>
    );
  }
  return <Inventory hotelId={hotelId} roomId={roomId} />;
}

function Inventory({ hotelId, roomId }: { hotelId: number; roomId: number }) {
  const inventory = useInventory(roomId);
  const update = useUpdateInventory(roomId);
  const [shown, setShown] = useState(PAGE);
  const schema = useMemo(() => inventorySchema(), []);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<InventoryValues>({
    resolver: zodResolver(schema),
    defaultValues: { startDate: today(), endDate: addDays(today(), 6), surgeFactor: 1, closed: false },
  });

  const upcoming = (inventory.data ?? []).filter((night) => night.date >= today());

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <header>
        <Link
          to={`/manage/hotels/${String(hotelId)}`}
          className="text-sm text-brand-700 underline underline-offset-4"
        >
          Back to the hotel
        </Link>
        <h1 className="mt-3 text-2xl font-semibold tracking-tight text-ink">Prices and availability</h1>
        <p className="mt-1 text-ink-soft">
          Each night&apos;s price is the base price times a factor. Raise the factor for busy nights, or close
          nights to stop bookings.
        </p>
      </header>

      <Card>
        <h2 className="text-lg font-semibold text-ink">Change a range of nights</h2>
        <form
          noValidate
          aria-label="Change nights"
          className="mt-4 grid gap-4 sm:grid-cols-2"
          onSubmit={(event) => {
            void handleSubmit((values) => {
              update.mutate(values);
            })(event);
          }}
        >
          <Input
            label="First night"
            type="date"
            min={today()}
            error={errors.startDate?.message}
            {...register('startDate')}
          />
          <Input
            label="Last night"
            type="date"
            min={today()}
            error={errors.endDate?.message}
            {...register('endDate')}
          />
          <Input
            label="Price factor"
            type="number"
            step="0.01"
            min={MIN_SURGE}
            max={MAX_SURGE}
            hint={`From ${String(MIN_SURGE)} to ${String(MAX_SURGE)}. 1 keeps the normal price.`}
            error={errors.surgeFactor?.message}
            {...register('surgeFactor', { valueAsNumber: true })}
          />
          <label className="flex items-center gap-3 self-center text-sm text-ink">
            <input type="checkbox" className="size-4 accent-brand-600" {...register('closed')} />
            Close these nights for booking
          </label>

          {update.isError ? (
            <p role="alert" className="text-sm text-danger sm:col-span-2">
              {describeRequestError(update.error)}
            </p>
          ) : null}
          {update.isSuccess ? (
            <p role="status" className="text-sm text-success sm:col-span-2">
              The nights were updated.
            </p>
          ) : null}
          <div className="sm:col-span-2">
            <Button type="submit" loading={update.isPending}>
              Apply to these nights
            </Button>
          </div>
        </form>
      </Card>

      <section aria-labelledby="nights-heading">
        <h2 id="nights-heading" className="mb-3 text-lg font-semibold text-ink">
          Upcoming nights
        </h2>
        {inventory.isPending ? (
          <div role="status" aria-label="Loading nights">
            <Skeleton className="h-40" />
          </div>
        ) : inventory.isError ? (
          <EmptyState title="We could not load the nights" description={inventory.error.message}>
            <Button
              onClick={() => {
                void inventory.refetch();
              }}
            >
              Try again
            </Button>
          </EmptyState>
        ) : upcoming.length === 0 ? (
          <EmptyState
            title="No upcoming nights"
            description="Nights are created when the hotel is put on sale. Put the hotel on sale first."
          />
        ) : (
          <>
            <div className="overflow-x-auto rounded-card border border-line bg-white">
              <table className="w-full text-left text-sm">
                <caption className="sr-only">Nightly prices and availability</caption>
                <thead className="border-b border-line text-ink-muted">
                  <tr>
                    <th scope="col" className="px-4 py-3 font-medium">
                      Night
                    </th>
                    <th scope="col" className="px-4 py-3 font-medium">
                      Price
                    </th>
                    <th scope="col" className="px-4 py-3 font-medium">
                      Free
                    </th>
                    <th scope="col" className="px-4 py-3 font-medium">
                      Status
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {upcoming.slice(0, shown).map((night) => (
                    <tr key={night.id} className="border-b border-line last:border-0">
                      <td className="px-4 py-3 text-ink">{formatDate(night.date)}</td>
                      <td className="px-4 py-3 text-ink">
                        {formatPrice(night.price)}
                        {night.surgeFactor !== 1 ? (
                          <span className="ml-2 text-xs text-ink-muted">x{night.surgeFactor}</span>
                        ) : null}
                      </td>
                      <td className="px-4 py-3 text-ink-soft">
                        {night.totalCount - night.bookedCount - night.reservedCount} of {night.totalCount}
                      </td>
                      <td className="px-4 py-3">
                        <Badge tone={night.closed ? 'danger' : 'success'}>
                          {night.closed ? 'Closed' : 'Open'}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {shown < upcoming.length ? (
              <div className="mt-4 flex justify-center">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setShown((count) => count + PAGE);
                  }}
                >
                  Show more nights
                </Button>
              </div>
            ) : null}
          </>
        )}
      </section>
    </div>
  );
}
