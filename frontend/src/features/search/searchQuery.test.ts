import { parseSearchParams, searchSchema, toSearchParams } from './searchQuery';

const now = new Date(2026, 10, 17);
const values = { city: 'Pune', startDate: '2026-12-20', endDate: '2026-12-22', rooms: 2 };

describe('searchSchema', () => {
  const messages = (value: unknown) =>
    searchSchema(now)
      .safeParse(value)
      .error?.issues.map((i) => i.message) ?? [];

  it('accepts a valid search', () => {
    expect(searchSchema(now).safeParse(values).success).toBe(true);
  });

  it('trims the city', () => {
    expect(searchSchema(now).parse({ ...values, city: '  Pune ' }).city).toBe('Pune');
  });

  it('asks for a city', () => {
    expect(messages({ ...values, city: '   ' })).toEqual(['Enter a city.']);
  });

  it('applies the stay rules', () => {
    expect(messages({ ...values, startDate: '2026-11-01' })).toContain('Check-in cannot be in the past.');
    expect(messages({ ...values, endDate: '2026-12-20' })).toContain('Check-out must be after check-in.');
  });
});

describe('search URL parameters', () => {
  it('round-trips a search, leaving page 1 implicit', () => {
    const params = toSearchParams(values);

    expect(params.has('page')).toBe(false);
    expect(parseSearchParams(params, now)).toEqual({ ...values, page: 1 });
  });

  it('keeps a later page', () => {
    const params = toSearchParams({ ...values, page: 3 });

    expect(params.get('page')).toBe('3');
    expect(parseSearchParams(params, now)?.page).toBe(3);
  });

  it('encodes a city with spaces and punctuation safely', () => {
    const city = 'Rishikesh & Haridwar';

    expect(parseSearchParams(toSearchParams({ ...values, city }), now)?.city).toBe(city);
  });

  it.each([
    ['no city', 'startDate=2026-12-20&endDate=2026-12-22'],
    ['a blank city', 'city=%20&startDate=2026-12-20&endDate=2026-12-22'],
    ['bad dates', 'city=Pune&startDate=2026-11-01&endDate=2026-11-03'],
    ['nothing at all', ''],
  ])('rejects %s', (_name, query) => {
    expect(parseSearchParams(new URLSearchParams(query), now)).toBeNull();
  });

  it.each(['0', '-2', 'abc', '1.5'])('falls back to page 1 for the page value %s', (page) => {
    const params = toSearchParams(values);
    params.set('page', page);

    expect(parseSearchParams(params, now)?.page).toBe(1);
  });
});
