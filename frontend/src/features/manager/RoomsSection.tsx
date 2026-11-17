import { useState } from 'react';

import { describeRequestError } from '@/shared/api/errors';
import { formatPrice, pluralize } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';
import { LinkButton } from '@/shared/ui/LinkButton';
import { Skeleton } from '@/shared/ui/Skeleton';

import { RoomForm } from './RoomForm';
import type { Catalog } from './useCatalog';
import { type SavedRoom, useDeleteRoom, useRooms } from './useRooms';

interface RoomsSectionProps {
  hotelId: number;
  catalog: Catalog;
  /** Live hotels have nightly inventory, so their rooms also offer price and availability controls. */
  live: boolean;
}

type Mode =
  | { kind: 'idle' }
  | { kind: 'add' }
  | { kind: 'edit'; room: SavedRoom }
  | { kind: 'delete'; room: SavedRoom };

/** The hotel's room types: what they are, what they cost, and the controls to change or remove them. */
export function RoomsSection({ hotelId, catalog, live }: RoomsSectionProps) {
  const rooms = useRooms(hotelId);
  const remove = useDeleteRoom(hotelId);
  const [mode, setMode] = useState<Mode>({ kind: 'idle' });
  const close = () => {
    setMode({ kind: 'idle' });
  };

  return (
    <section aria-labelledby="rooms-heading" className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <h2 id="rooms-heading" className="text-xl font-semibold text-ink">
          Room types
        </h2>
        {mode.kind === 'idle' ? (
          <Button
            onClick={() => {
              setMode({ kind: 'add' });
            }}
          >
            Add a room type
          </Button>
        ) : null}
      </div>

      {mode.kind === 'add' ? (
        <Card>
          <RoomForm hotelId={hotelId} catalog={catalog} onSaved={close} onCancel={close} />
        </Card>
      ) : null}

      {rooms.isPending ? (
        <div role="status" aria-label="Loading room types">
          <Skeleton className="h-24" />
        </div>
      ) : rooms.isError ? (
        <EmptyState title="We could not load the room types" description={rooms.error.message}>
          <Button
            onClick={() => {
              void rooms.refetch();
            }}
          >
            Try again
          </Button>
        </EmptyState>
      ) : rooms.data.length === 0 && mode.kind !== 'add' ? (
        <EmptyState
          title="No room types yet"
          description="Add at least one room type before putting the hotel on sale."
        />
      ) : (
        <ul className="flex flex-col gap-3">
          {rooms.data.map((room) => (
            <li key={room.id}>
              {mode.kind === 'edit' && mode.room.id === room.id ? (
                <Card>
                  <RoomForm
                    hotelId={hotelId}
                    catalog={catalog}
                    initial={room}
                    onSaved={close}
                    onCancel={close}
                  />
                </Card>
              ) : (
                <Card className="flex flex-col gap-3">
                  <div className="flex flex-wrap items-start justify-between gap-4">
                    <div>
                      <h3 className="text-lg font-semibold text-ink">{room.type}</h3>
                      <p className="text-sm text-ink-soft">
                        {pluralize(room.totalCount, 'room')} · up to {pluralize(room.capacity, 'guest')} each
                      </p>
                      {room.amenities && room.amenities.length > 0 ? (
                        <ul aria-label={`${room.type} amenities`} className="mt-2 flex flex-wrap gap-1.5">
                          {room.amenities.map((amenity) => (
                            <li key={amenity}>
                              <Badge>{amenity}</Badge>
                            </li>
                          ))}
                        </ul>
                      ) : null}
                    </div>
                    <p className="text-right">
                      <span className="text-xl font-semibold text-ink">{formatPrice(room.basePrice)}</span>
                      <span className="block text-xs text-ink-muted">base price per night</span>
                    </p>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    {live ? (
                      <LinkButton
                        to={`/manage/hotels/${String(hotelId)}/rooms/${String(room.id)}/inventory`}
                        variant="secondary"
                      >
                        Prices and availability
                      </LinkButton>
                    ) : null}
                    <Button
                      variant="secondary"
                      aria-label={`Edit ${room.type}`}
                      onClick={() => {
                        setMode({ kind: 'edit', room });
                      }}
                    >
                      Edit
                    </Button>
                    <Button
                      variant="ghost"
                      aria-label={`Delete ${room.type}`}
                      onClick={() => {
                        setMode({ kind: 'delete', room });
                      }}
                    >
                      Delete
                    </Button>
                  </div>

                  {mode.kind === 'delete' && mode.room.id === room.id ? (
                    <div
                      role="group"
                      aria-label={`Confirm deleting ${room.type}`}
                      className="rounded-lg border border-line bg-canvas p-4"
                    >
                      <p className="text-sm text-ink">
                        Delete the {room.type} room type? Its nightly inventory is removed too. This is not
                        possible if it has bookings.
                      </p>
                      {remove.isError ? (
                        <p role="alert" className="mt-2 text-sm text-danger">
                          {describeRequestError(remove.error)}
                        </p>
                      ) : null}
                      <div className="mt-3 flex gap-3">
                        <Button
                          variant="danger"
                          loading={remove.isPending}
                          onClick={() => {
                            remove.mutate(room.id, { onSuccess: close });
                          }}
                        >
                          Yes, delete
                        </Button>
                        <Button variant="secondary" onClick={close}>
                          Keep
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </Card>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
