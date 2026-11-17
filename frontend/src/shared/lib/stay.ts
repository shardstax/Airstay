import { z } from 'zod';

/** The longest stay the backend accepts (airstay.booking.max-nights). Checked here so the form can say so early. */
export const MAX_NIGHTS = 30;
export const MAX_ROOMS = 10;

const MS_PER_DAY = 86_400_000;

// Dates are plain `YYYY-MM-DD` strings, the way the API sends and expects them. Arithmetic goes through UTC midnight so
// daylight-saving changes can never make a day 23 or 25 hours long.
function utcMs(iso: string): number {
  const [year = 0, month = 1, day = 1] = iso.split('-').map(Number);
  return Date.UTC(year, month - 1, day);
}

function toIso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Today in the user's own calendar. */
export function today(now = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${String(now.getFullYear())}-${month}-${day}`;
}

/** `iso` moved by whole days; month and year boundaries roll over correctly. */
export function addDays(iso: string, days: number): string {
  return toIso(new Date(utcMs(iso) + days * MS_PER_DAY));
}

/** Number of nights between check-in and check-out (the check-out day is not a night). */
export function nightsBetween(startDate: string, endDate: string): number {
  return Math.round((utcMs(endDate) - utcMs(startDate)) / MS_PER_DAY);
}

export interface Stay {
  startDate: string;
  endDate: string;
  rooms: number;
}

/** A sensible starting point for a search: tomorrow, two nights, one room. */
export function defaultStay(now = new Date()): Stay {
  const startDate = addDays(today(now), 1);
  return { startDate, endDate: addDays(startDate, 2), rooms: 1 };
}

/** The fields of a stay with the rules the backend also enforces. Add the date-order check with `checkStayDates`. */
export function stayShape(now = new Date()) {
  return {
    startDate: z.iso
      .date('Choose a check-in date.')
      .refine((date) => date >= today(now), 'Check-in cannot be in the past.'),
    endDate: z.iso.date('Choose a check-out date.'),
    rooms: z
      .number('Choose how many rooms.')
      .int()
      .min(1, 'Book at least one room.')
      .max(MAX_ROOMS, `Book at most ${String(MAX_ROOMS)} rooms.`),
  };
}

/** Use with `.superRefine(checkStayDates)` on a schema built from `stayShape`. */
export function checkStayDates(value: Pick<Stay, 'startDate' | 'endDate'>, ctx: z.RefinementCtx): void {
  const nights = nightsBetween(value.startDate, value.endDate);
  if (nights < 1) {
    ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'Check-out must be after check-in.' });
  } else if (nights > MAX_NIGHTS) {
    ctx.addIssue({
      code: 'custom',
      path: ['endDate'],
      message: `Stays are limited to ${String(MAX_NIGHTS)} nights.`,
    });
  }
}

/** The stay as URL query parameters, so a search or hotel link can be shared, bookmarked and reloaded. */
export function stayToParams(stay: Stay): URLSearchParams {
  return new URLSearchParams({ startDate: stay.startDate, endDate: stay.endDate, rooms: String(stay.rooms) });
}

/** Reads a stay back from the URL; null when anything is missing or breaks a rule (a hand-edited or outdated link). */
export function parseStay(params: URLSearchParams, now = new Date()): Stay | null {
  const result = z
    .object(stayShape(now))
    .superRefine(checkStayDates)
    .safeParse({
      startDate: params.get('startDate'),
      endDate: params.get('endDate'),
      rooms: Number(params.get('rooms') ?? 1),
    });
  return result.success ? result.data : null;
}
