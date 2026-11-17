/** A centred loading indicator that screen readers announce. */
export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center py-16">
      <span
        aria-hidden
        className="size-6 animate-spin rounded-full border-2 border-brand-600 border-t-transparent"
      />
      <span className="sr-only">{label}</span>
    </div>
  );
}
