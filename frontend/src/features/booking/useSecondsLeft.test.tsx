import { act, render, renderHook, screen } from '@testing-library/react';

import { HoldBanner } from './HoldBanner';
import { formatClock, useSecondsLeft } from './useSecondsLeft';

const NOW = new Date('2026-12-01T10:00:00Z');
const inSeconds = (seconds: number) => new Date(NOW.getTime() + seconds * 1000).toISOString();

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
});
afterEach(() => {
  vi.useRealTimers();
});

describe('formatClock', () => {
  it.each([
    [600, '10:00'],
    [605, '10:05'],
    [59, '0:59'],
    [60, '1:00'],
    [9, '0:09'],
    [0, '0:00'],
  ])('writes %i seconds as %s', (seconds, text) => {
    expect(formatClock(seconds)).toBe(text);
  });
});

describe('useSecondsLeft', () => {
  it('counts down once a second', () => {
    const { result } = renderHook(() => useSecondsLeft(inSeconds(10)));
    expect(result.current).toBe(10);

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(result.current).toBe(7);
  });

  it('stops at zero and never goes negative', () => {
    const { result } = renderHook(() => useSecondsLeft(inSeconds(2)));

    act(() => {
      vi.advanceTimersByTime(10_000);
    });

    expect(result.current).toBe(0);
  });

  it('rounds a partial second up, so the clock never shows 0:00 before the hold has really ended', () => {
    const { result } = renderHook(() => useSecondsLeft(inSeconds(0.4)));

    expect(result.current).toBe(1);
  });

  it('is null when there is no expiry', () => {
    const { result } = renderHook(() => useSecondsLeft(undefined));

    expect(result.current).toBeNull();
  });

  it('starts counting towards a new expiry when it changes', () => {
    const { result, rerender } = renderHook(({ at }) => useSecondsLeft(at), {
      initialProps: { at: inSeconds(10) },
    });

    rerender({ at: inSeconds(100) });

    expect(result.current).toBe(100);
  });
});

describe('HoldBanner', () => {
  it('shows the time left', () => {
    render(<HoldBanner expiresAt={inSeconds(605)} onExpired={() => undefined} />);

    expect(screen.getByRole('timer')).toHaveTextContent('10:05');
  });

  it('updates as time passes', () => {
    render(<HoldBanner expiresAt={inSeconds(605)} onExpired={() => undefined} />);

    act(() => {
      vi.advanceTimersByTime(65_000);
    });

    expect(screen.getByRole('timer')).toHaveTextContent('9:00');
  });

  it('warns in the last two minutes', () => {
    render(<HoldBanner expiresAt={inSeconds(119)} onExpired={() => undefined} />);

    expect(screen.getByRole('timer')).toHaveClass('bg-warning-bg');
  });

  it('is calm while there is plenty of time', () => {
    render(<HoldBanner expiresAt={inSeconds(300)} onExpired={() => undefined} />);

    expect(screen.getByRole('timer')).not.toHaveClass('bg-warning-bg');
  });

  it('says the hold has ended and tells the page exactly once', () => {
    const onExpired = vi.fn();
    render(<HoldBanner expiresAt={inSeconds(2)} onExpired={onExpired} />);

    act(() => {
      vi.advanceTimersByTime(10_000);
    });

    expect(screen.getByRole('timer')).toHaveTextContent('The hold on your room has ended.');
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('shows nothing without an expiry', () => {
    render(<HoldBanner expiresAt={undefined} onExpired={() => undefined} />);

    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
  });
});
