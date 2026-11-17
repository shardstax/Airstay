import { z } from 'zod';

import { isHttpsUrl } from '@/shared/lib/url';

export const MAX_PHOTOS = 10;
export const MAX_HOTEL_AMENITIES = 15;

// The backend's phone rule: an optional leading + or bracket, then digits with spaces, brackets or dashes (7 to 20 characters).
const PHONE = /^[+(]?[0-9][0-9 ()-]{6,19}$/;

/**
 * The hotel form. The place and the amenities come from fixed lists, so a value outside them cannot be entered:
 * `placeId` only exists once a place was chosen from the picker, and amenities must be among the catalog's.
 */
export function hotelSchema(amenityChoices: readonly string[]) {
  return z.object({
    name: z.string().trim().min(2, 'Use at least 2 characters.').max(100, 'Use at most 100 characters.'),
    placeLabel: z.string().min(1, 'Choose a place from the list.'),
    // Held as text (empty = nothing chosen yet) so the form can clear it; turned into a number once it is valid.
    placeId: z
      .string()
      .regex(/^[1-9][0-9]*$/, 'Choose a place from the list.')
      .transform(Number),
    amenities: z
      .array(z.string())
      .max(MAX_HOTEL_AMENITIES, `Choose at most ${String(MAX_HOTEL_AMENITIES)} amenities.`)
      .refine(
        (chosen) => chosen.every((amenity) => amenityChoices.includes(amenity)),
        'Choose from the list.',
      ),
    photos: z
      .array(z.string().refine(isHttpsUrl, 'Photos must be https addresses.'))
      .max(MAX_PHOTOS, `Add at most ${String(MAX_PHOTOS)} photos.`),
    address: z.string().trim().max(255, 'Use at most 255 characters.'),
    phoneNumber: z.union([
      z.literal(''),
      z.string().regex(PHONE, 'Enter a phone number such as +91 98765 43210.'),
    ]),
    email: z.union([
      z.literal(''),
      z.email('Enter a valid email address.').max(255, 'Use at most 255 characters.'),
    ]),
  });
}

/** What the form holds while it is being filled in. */
export type HotelFormValues = z.input<ReturnType<typeof hotelSchema>>;
/** The checked values, ready to send. */
export type HotelValues = z.output<ReturnType<typeof hotelSchema>>;

/** The request body for a form entry: empty contact fields are left out. */
export function toHotelRequest(values: HotelValues) {
  const contactInfo = {
    ...(values.address ? { address: values.address } : {}),
    ...(values.phoneNumber ? { phoneNumber: values.phoneNumber } : {}),
    ...(values.email ? { email: values.email } : {}),
  };
  return {
    name: values.name,
    placeId: values.placeId,
    amenities: values.amenities,
    photos: values.photos,
    ...(Object.keys(contactInfo).length > 0 ? { contactInfo } : {}),
  };
}
