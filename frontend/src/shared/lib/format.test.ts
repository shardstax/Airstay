import { formatDate, formatPrice, pluralize } from './format';

describe('formatPrice', () => {
  // Intl separates the symbol and digits with a non-breaking space in some locales; compare the visible text.
  const plain = (text: string) => text.replace(/\s/g, ' ');

  it('shows whole rupees with Indian grouping', () => {
    expect(plain(formatPrice(2000))).toBe('₹2,000');
    expect(plain(formatPrice(125000))).toBe('₹1,25,000');
  });

  it('rounds fractions to the nearest rupee', () => {
    expect(plain(formatPrice(1999.6))).toBe('₹2,000');
    expect(plain(formatPrice(1500.4))).toBe('₹1,500');
  });
});

describe('formatDate', () => {
  it('writes a day, short month and year', () => {
    expect(formatDate('2026-12-20')).toBe('20 Dec 2026');
    expect(formatDate('2027-01-05')).toBe('5 Jan 2027');
  });

  it('does not shift the day for the viewer’s time zone', () => {
    expect(formatDate('2026-03-01')).toBe('1 Mar 2026');
    expect(formatDate('2026-12-31')).toBe('31 Dec 2026');
  });
});

describe('pluralize', () => {
  it('uses the singular only for exactly one', () => {
    expect(pluralize(1, 'night')).toBe('1 night');
    expect(pluralize(2, 'night')).toBe('2 nights');
    expect(pluralize(0, 'room')).toBe('0 rooms');
  });
});
