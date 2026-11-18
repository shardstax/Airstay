import { z } from 'zod';

import { isPhotoUrl } from '@/shared/lib/url';

import { MAX_PHOTOS } from './hotelSchema';

export const MAX_ROOM_AMENITIES = 15;

/** True when a price has at most two decimal places (whole paise), allowing for floating-point noise. */
function hasAtMostTwoDecimals(value: number): boolean {
  return Math.abs(value * 100 - Math.round(value * 100)) < 1e-6;
}

/** The room type form, with the backend's limits. The type and amenities come from the catalog's fixed lists. */
export function roomSchema(typeChoices: readonly string[], amenityChoices: readonly string[]) {
  return z.object({
    type: z.string().refine((type) => typeChoices.includes(type), 'Choose a room type from the list.'),
    basePrice: z
      .number('Enter a price.')
      .min(0.01, 'The price must be more than zero.')
      .max(99_999_999.99, 'That price is too high.')
      .refine(hasAtMostTwoDecimals, 'Use at most two decimal places.'),
    totalCount: z
      .number('Enter how many rooms of this type there are.')
      .int('Use a whole number.')
      .min(1, 'There must be at least 1 room.')
      .max(1000, 'At most 1000 rooms.'),
    capacity: z
      .number('Enter how many guests fit in one room.')
      .int('Use a whole number.')
      .min(1, 'At least 1 guest.')
      .max(20, 'At most 20 guests per room.'),
    photos: z
      .array(z.string().refine(isPhotoUrl, 'Upload a photo or enter an HTTPS address.'))
      .max(MAX_PHOTOS)
      .optional(),
    amenities: z
      .array(z.string())
      .max(MAX_ROOM_AMENITIES, `Choose at most ${String(MAX_ROOM_AMENITIES)} amenities.`)
      .refine(
        (chosen) => chosen.every((amenity) => amenityChoices.includes(amenity)),
        'Choose from the list.',
      ),
  });
}

export type RoomValues = z.infer<ReturnType<typeof roomSchema>>;
