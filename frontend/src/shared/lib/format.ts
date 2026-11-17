const rupees = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});
const longDate = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

/** An amount in rupees, whole numbers, grouped the Indian way: 1,25,000. */
export function formatPrice(amount: number): string {
  return rupees.format(amount);
}

/** A `YYYY-MM-DD` date as "20 Dec 2026", independent of the viewer's time zone. */
export function formatDate(iso: string): string {
  return longDate.format(new Date(`${iso}T00:00:00Z`));
}

/** "1 night", "2 nights". */
export function pluralize(count: number, noun: string): string {
  return `${String(count)} ${noun}${count === 1 ? '' : 's'}`;
}
