import { z } from 'zod';

import {
  addDays,
  checkStayDates,
  defaultStay,
  MAX_NIGHTS,
  nightsBetween,
  parseStay,
  stayShape,
  stayToParams,
  today,
} from './stay';

describe('today', () => {
  it('uses the local calendar day, zero padded', () => {
    expect(today(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
    expect(today(new Date(2026, 11, 31, 0, 1))).toBe('2026-12-31');
  });
});

describe('addDays', () => {
  it.each([
    ['2026-11-17', 1, '2026-11-18'],
    ['2026-11-30', 1, '2026-12-01'],
    ['2026-12-31', 1, '2027-01-01'],
    ['2028-02-28', 1, '2028-02-29'],
    ['2027-02-28', 1, '2027-03-01'],
    ['2026-03-01', -1, '2026-02-28'],
    ['2026-11-17', 0, '2026-11-17'],
  ])('%s plus %i day(s) is %s', (start, days, expected) => {
    expect(addDays(start, days)).toBe(expected);
  });

  it('is not thrown off by a daylight-saving change', () => {
    expect(addDays('2026-03-28', 2)).toBe('2026-03-30');
    expect(addDays('2026-10-24', 2)).toBe('2026-10-26');
  });
});

describe('nightsBetween', () => {
  it.each([
    ['2026-12-20', '2026-12-22', 2],
    ['2026-12-31', '2027-01-02', 2],
    ['2026-03-28', '2026-03-30', 2],
    ['2026-12-20', '2026-12-20', 0],
    ['2026-12-22', '2026-12-20', -2],
  ])('from %s to %s is %i night(s)', (start, end, nights) => {
    expect(nightsBetween(start, end)).toBe(nights);
  });
});

describe('defaultStay', () => {
  it('starts tomorrow for two nights in one room', () => {
    expect(defaultStay(new Date(2026, 10, 17, 15, 0))).toEqual({
      startDate: '2026-11-18',
      endDate: '2026-11-20',
      rooms: 1,
    });
  });
});

describe('the stay rules', () => {
  const now = new Date(2026, 10, 17);
  const schema = z.object(stayShape(now)).superRefine(checkStayDates);
  const valid = { startDate: '2026-11-20', endDate: '2026-11-22', rooms: 1 };
  const messages = (value: unknown) =>
    schema.safeParse(value).error?.issues.map((issue) => issue.message) ?? [];

  it('accepts a valid stay, including check-in today', () => {
    expect(schema.safeParse(valid).success).toBe(true);
    expect(schema.safeParse({ ...valid, startDate: '2026-11-17' }).success).toBe(true);
  });

  it('rejects a check-in date in the past', () => {
    expect(messages({ ...valid, startDate: '2026-11-16' })).toEqual(['Check-in cannot be in the past.']);
  });

  it('rejects check-out on or before check-in', () => {
    expect(messages({ ...valid, endDate: '2026-11-20' })).toEqual(['Check-out must be after check-in.']);
    expect(messages({ ...valid, endDate: '2026-11-19' })).toEqual(['Check-out must be after check-in.']);
  });

  it('limits a stay to the nights the backend allows', () => {
    expect(schema.safeParse({ ...valid, endDate: addDays('2026-11-20', MAX_NIGHTS) }).success).toBe(true);
    expect(messages({ ...valid, endDate: addDays('2026-11-20', MAX_NIGHTS + 1) })).toEqual([
      'Stays are limited to 30 nights.',
    ]);
  });

  it('limits the number of rooms to 1 to 10', () => {
    expect(schema.safeParse({ ...valid, rooms: 10 }).success).toBe(true);
    expect(messages({ ...valid, rooms: 0 })).toEqual(['Book at least one room.']);
    expect(messages({ ...valid, rooms: 11 })).toEqual(['Book at most 10 rooms.']);
  });

  it('rejects text that is not a date', () => {
    expect(schema.safeParse({ ...valid, startDate: 'tomorrow' }).success).toBe(false);
    expect(schema.safeParse({ ...valid, endDate: '' }).success).toBe(false);
  });
});

describe('stay URL parameters', () => {
  const now = new Date(2026, 10, 17);
  const stay = { startDate: '2026-12-20', endDate: '2026-12-22', rooms: 2 };

  it('round-trips a stay', () => {
    expect(parseStay(stayToParams(stay), now)).toEqual(stay);
  });

  it('defaults to one room when the parameter is missing', () => {
    expect(parseStay(new URLSearchParams('startDate=2026-12-20&endDate=2026-12-22'), now)?.rooms).toBe(1);
  });

  it.each([
    ['missing dates', ''],
    ['a past check-in', 'startDate=2026-11-01&endDate=2026-11-03'],
    ['check-out before check-in', 'startDate=2026-12-22&endDate=2026-12-20'],
    ['text instead of a date', 'startDate=soon&endDate=later'],
    ['a room count that is not a number', 'startDate=2026-12-20&endDate=2026-12-22&rooms=many'],
    ['too many rooms', 'startDate=2026-12-20&endDate=2026-12-22&rooms=11'],
    ['a fractional room count', 'startDate=2026-12-20&endDate=2026-12-22&rooms=1.5'],
  ])('rejects %s', (_name, query) => {
    expect(parseStay(new URLSearchParams(query), now)).toBeNull();
  });
});
