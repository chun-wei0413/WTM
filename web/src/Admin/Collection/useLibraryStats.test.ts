// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LibraryStats } from '../../api/types';
import { useLibraryStats } from './useLibraryStats';

const { stats } = vi.hoisted(() => ({ stats: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { collection: { stats } } } }));

function numbers(patch: Partial<LibraryStats> = {}): LibraryStats {
  return { total: 10, approved: 8, retired: 0, waitingForTags: 0, beingTagged: 0, tagFailures: 0, ...patch };
}

beforeEach(() => {
  stats.mockReset().mockResolvedValue(numbers());
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('useLibraryStats', () => {
  it('has no numbers until the server has answered', async () => {
    const { result } = renderHook(() => useLibraryStats());

    expect(result.current.stats).toBeNull();
    await waitFor(() => expect(result.current.stats).toEqual(numbers()));
    expect(result.current.pending).toBe(0);
  });

  it('keeps the error, and forgets it when the numbers can be read again', async () => {
    stats.mockRejectedValueOnce(new Error('down'));
    const { result } = renderHook(() => useLibraryStats());
    await waitFor(() => expect(result.current.error).not.toBeNull());
    expect(result.current.stats).toBeNull();

    await act(() => result.current.refresh());

    expect(result.current.error).toBeNull();
    expect(result.current.stats).toEqual(numbers());
  });

  it('counts the pictures waiting for tags and the ones being tagged', async () => {
    stats.mockResolvedValue(numbers({ waitingForTags: 3, beingTagged: 2 }));

    const { result } = renderHook(() => useLibraryStats());

    await waitFor(() => expect(result.current.pending).toBe(5));
  });

  it('looks again every few seconds while pictures wait for tags, and stops when none do', async () => {
    vi.useFakeTimers();
    stats
      .mockResolvedValueOnce(numbers({ waitingForTags: 2 }))
      .mockResolvedValueOnce(numbers({ waitingForTags: 1 }))
      .mockResolvedValue(numbers());

    const { result } = renderHook(() => useLibraryStats());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(stats).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_000);
    });
    expect(stats).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(3_000);
    });
    expect(stats).toHaveBeenCalledTimes(3);
    expect(result.current.pending).toBe(0);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(stats).toHaveBeenCalledTimes(3);
  });

  it('does not look again by itself when nothing waits', async () => {
    vi.useFakeTimers();

    renderHook(() => useLibraryStats());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });

    expect(stats).toHaveBeenCalledTimes(1);
  });
});
