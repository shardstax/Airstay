import { formatPrice, pluralize } from '@/shared/lib/format';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';

import type { RoomOffer } from './useHotelInfo';

interface RoomOfferCardProps {
  room: RoomOffer;
  nights: number;
  rooms: number;
  onReserve: () => void;
  /** True while a hold is being placed, so a second click cannot start another. */
  busy: boolean;
}

/** One room type: what it offers, the average price per night, what the whole stay would cost, and a way to reserve it. */
export function RoomOfferCard({ room, nights, rooms, onReserve, busy }: RoomOfferCardProps) {
  const amenities = room.amenities ?? [];
  const total = room.price * nights * rooms;

  return (
    <Card className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <h3 className="text-lg font-semibold text-ink">{room.type}</h3>
        {amenities.length > 0 ? (
          <ul aria-label={`${room.type} amenities`} className="mt-2 flex flex-wrap gap-1.5">
            {amenities.map((amenity) => (
              <li key={amenity}>
                <Badge>{amenity}</Badge>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

      <div className="flex shrink-0 flex-col gap-3 sm:items-end sm:text-right">
        <div>
          <p>
            <span className="text-xl font-semibold text-ink">{formatPrice(room.price)}</span>
            <span className="text-sm text-ink-muted"> per night</span>
          </p>
          <p className="text-sm text-ink-soft">
            {formatPrice(total)} for {pluralize(nights, 'night')}
            {rooms > 1 ? `, ${pluralize(rooms, 'room')}` : ''}
          </p>
        </div>
        <Button aria-label={`Reserve ${room.type}`} loading={busy} onClick={onReserve}>
          Reserve
        </Button>
      </div>
    </Card>
  );
}
