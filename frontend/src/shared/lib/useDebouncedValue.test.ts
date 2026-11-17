import { act, renderHook } from '@testing-library/react';

import { useDebouncedValue } from './useDebouncedValue';

beforeEach(() => {
  vi.useFakeTimers();
});
afterEach(() => {
  vi.useRealTimers();
});

describe('useDebouncedValue', () => {
  it('starts with the value it is given', () => {
    const { result } = renderHook(() => useDebouncedValue('pu', 250));

    expect(result.current).toBe('pu');
  });

  it('waits until the value has stopped changing', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 250), {
      initialProps: { value: 'p' },
    });

    rerender({ value: 'pu' });
    act(() => {
      vi.advanceTimersByTime(200);
    });
    rerender({ value: 'pun' });
    act(() => {
      vi.advanceTimersByTime(200);
    });

    expect(result.current).toBe('p');

    act(() => {
      vi.advanceTimersByTime(60);
    });
    expect(result.current).toBe('pun');
  });
});
