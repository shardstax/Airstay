import { Link } from 'react-router-dom';

import { formatPrice } from '@/shared/lib/format';
import { type Stay, stayToParams } from '@/shared/lib/stay';
import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';

import type { HotelSummary } from './useHotelSearch';

const VISIBLE_AMENITIES = 4;

interface HotelCardProps {
  hotel: HotelSummary;
  stay: Stay;
}

/** One search result: a photo, the hotel, its amenities and the price of its cheapest room per night. */
export function HotelCard({ hotel, stay }: HotelCardProps) {
  const photo = hotel.photos?.[0];
  const amenities = hotel.amenities ?? [];
  const to = `/hotels/${String(hotel.id)}?${stayToParams(stay).toString()}`;

  return (
    <Card className="relative flex flex-col gap-5 rounded-2xl p-4 transition-shadow hover:shadow-md sm:flex-row sm:items-center">
      {photo ? (
        <img
          src={photo}
          alt=""
          loading="lazy"
          className="h-48 w-full shrink-0 rounded-xl object-cover sm:h-32 sm:w-48"
        />
      ) : (
        <div aria-hidden className="h-32 w-full shrink-0 rounded-xl bg-brand-50 sm:w-48" />
      )}

      <div className="min-w-0 flex-1">
        <h2 className="text-lg font-semibold text-ink">
          <Link to={to} className="after:absolute after:inset-0 hover:underline">
            {hotel.name}
          </Link>
        </h2>
        <p className="text-sm text-ink-muted">{hotel.city}</p>
        {amenities.length > 0 ? (
          <ul aria-label="Amenities" className="mt-2 flex flex-wrap gap-1.5">
            {amenities.slice(0, VISIBLE_AMENITIES).map((amenity) => (
              <li key={amenity}>
                <Badge>{amenity}</Badge>
              </li>
            ))}
            {amenities.length > VISIBLE_AMENITIES ? (
              <li>
                <Badge>+{amenities.length - VISIBLE_AMENITIES} more</Badge>
              </li>
            ) : null}
          </ul>
        ) : null}
      </div>

      <p className="shrink-0 sm:text-right">
        <span className="block text-xs text-ink-muted">From</span>
        <span className="text-xl font-semibold text-ink">{formatPrice(hotel.price)}</span>
        <span className="block text-xs text-ink-muted">per night</span>
      </p>
    </Card>
  );
}
