import { cn } from '@/shared/lib/cn';

/** A grey placeholder block shown while content loads. Decorative: pair it with a labelled status for screen readers. */
export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-lg bg-line', className)} />;
}
