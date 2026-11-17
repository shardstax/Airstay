import { useEffect, useState } from 'react';

/** Whole seconds until `expiresAt` (an ISO instant), counting down once a second; null when there is no expiry. Never below 0. */
export function useSecondsLeft(expiresAt: string | undefined): number | null {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!expiresAt) {
      return undefined;
    }
    // The clock starts from the time read when this mounted, and the first tick is one second away: a changed expiry
    // is therefore at most a second off before the next tick corrects it.
    const timer = setInterval(() => {
      setNow(Date.now());
    }, 1000);
    return () => {
      clearInterval(timer);
    };
  }, [expiresAt]);

  if (!expiresAt) {
    return null;
  }
  return Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 1000));
}

/** 605 seconds as "10:05". */
export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${String(minutes)}:${seconds}`;
}
