import { z } from 'zod';

import { today } from '@/shared/lib/stay';

/** Mirrors the backend's GuestDto rules: a name of up to 100 characters; gender and date of birth are optional. */
export function guestSchema(now = new Date()) {
  return z.object({
    name: z.string().trim().min(1, 'Enter the guest’s name.').max(100, 'Use at most 100 characters.'),
    gender: z.enum(['', 'MALE', 'FEMALE', 'OTHER']),
    dateOfBirth: z.union([
      z.literal(''),
      z.iso
        .date('Enter a valid date.')
        .refine((date) => date < today(now), 'Date of birth must be in the past.'),
    ]),
  });
}

export type GuestValues = z.infer<ReturnType<typeof guestSchema>>;

/** The request body for a form entry: optional fields left empty are left out. */
export function toGuest({ name, gender, dateOfBirth }: GuestValues) {
  return {
    name,
    ...(gender ? { gender } : {}),
    ...(dateOfBirth ? { dateOfBirth } : {}),
  };
}
