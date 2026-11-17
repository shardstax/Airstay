import { z } from 'zod';

import { today } from '@/shared/lib/stay';

/** Mirrors the backend's ProfileUpdateRequestDto: a name of 1 to 100 characters; gender and date of birth are optional. */
export function profileSchema(now = new Date()) {
  return z.object({
    name: z.string().trim().min(1, 'Enter your name.').max(100, 'Use at most 100 characters.'),
    gender: z.enum(['', 'MALE', 'FEMALE', 'OTHER']),
    dateOfBirth: z.union([
      z.literal(''),
      z.iso
        .date('Enter a valid date.')
        .refine((date) => date < today(now), 'Date of birth must be in the past.'),
    ]),
  });
}

export type ProfileValues = z.infer<ReturnType<typeof profileSchema>>;

/** The request body: empty optional fields are left out. */
export function toProfileUpdate({ name, gender, dateOfBirth }: ProfileValues) {
  return { name, ...(gender ? { gender } : {}), ...(dateOfBirth ? { dateOfBirth } : {}) };
}
