import { z } from 'zod';

import { today } from '@/shared/lib/stay';

/** The price-factor limits the backend enforces (0.50 to 5.00, two decimals). */
export const MIN_SURGE = 0.5;
export const MAX_SURGE = 5;

/** Changing a range of nights: a price factor and whether the nights are open for booking. */
export function inventorySchema(now = new Date()) {
  return z
    .object({
      startDate: z.iso
        .date('Choose the first night.')
        .refine((date) => date >= today(now), 'Nights in the past cannot be changed.'),
      endDate: z.iso.date('Choose the last night.'),
      surgeFactor: z
        .number('Enter a price factor.')
        .min(MIN_SURGE, `The factor must be at least ${String(MIN_SURGE)}.`)
        .max(MAX_SURGE, `The factor must be at most ${String(MAX_SURGE)}.`)
        .refine(
          (value) => Math.abs(value * 100 - Math.round(value * 100)) < 1e-6,
          'Use at most two decimal places.',
        ),
      closed: z.boolean(),
    })
    .refine((value) => value.endDate >= value.startDate, {
      path: ['endDate'],
      message: 'The last night cannot be before the first.',
    });
}

export type InventoryValues = z.infer<ReturnType<typeof inventorySchema>>;
