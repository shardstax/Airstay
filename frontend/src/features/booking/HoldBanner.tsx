import { useEffect, useRef } from 'react';

import { cn } from '@/shared/lib/cn';

import { formatClock, useSecondsLeft } from './useSecondsLeft';

interface HoldBannerProps {
  /** When the hold on the rooms ends (an ISO instant). */
  expiresAt: string | undefined;
  /** Called once when the clock reaches zero, so the page can ask the server what became of the booking. */
  onExpired: () => void;
}

const URGENT_SECONDS = 120;

/** The running clock on a held room: the guest has this long to finish before the rooms are released. */
export function HoldBanner({ expiresAt, onExpired }: HoldBannerProps) {
  const secondsLeft = useSecondsLeft(expiresAt);
  const latestOnExpired = useRef(onExpired);

  useEffect(() => {
    latestOnExpired.current = onExpired;
  });

  const ended = secondsLeft === 0;
  useEffect(() => {
    if (ended) {
      latestOnExpired.current();
    }
  }, [ended]);

  if (secondsLeft === null) {
    return null;
  }
  const urgent = secondsLeft <= URGENT_SECONDS;

  return (
    <div
      role="timer"
      aria-live="off"
      className={cn(
        'rounded-lg px-4 py-3 text-sm',
        urgent ? 'bg-warning-bg text-warning' : 'bg-brand-50 text-brand-800',
      )}
    >
      {ended ? (
        'The hold on your room has ended.'
      ) : (
        <>
          Your room is held for <strong className="tabular-nums">{formatClock(secondsLeft)}</strong>. Finish
          before the clock runs out or the room goes back on sale.
        </>
      )}
    </div>
  );
}
