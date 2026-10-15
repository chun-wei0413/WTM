// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useIndexSync } from './useIndexSync';

const { syncIndex } = vi.hoisted(() => ({ syncIndex: vi.fn() }));
vi.mock('../api/client', () => ({ api: { admin: { syncIndex } } }));

const onSynced = vi.fn();

beforeEach(() => {
  syncIndex.mockReset().mockResolvedValue({ indexed: 3, removed: 1, failed: 0 });
  onSynced.mockReset().mockResolvedValue(undefined);
});
afterEach(cleanup);

describe('useIndexSync', () => {
  it('starts with nothing synced', () => {
    const { result } = renderHook(() => useIndexSync(onSynced));

    expect(result.current).toMatchObject({ syncing: false, synced: null, syncError: null });
  });

  it('syncs, keeps what was done, and has the numbers read again', async () => {
    const { result } = renderHook(() => useIndexSync(onSynced));

    await act(() => result.current.sync());

    expect(result.current.synced).toEqual({ indexed: 3, removed: 1, failed: 0 });
    expect(onSynced).toHaveBeenCalledTimes(1);
    expect(result.current.syncing).toBe(false);
  });

  it('says it is syncing meanwhile', async () => {
    let finish!: (value: unknown) => void;
    syncIndex.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useIndexSync(onSynced));

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.sync();
    });
    expect(result.current.syncing).toBe(true);

    await act(async () => {
      finish({ indexed: 0, removed: 0, failed: 0 });
      await pending;
    });
    expect(result.current.syncing).toBe(false);
  });

  it('keeps the error of a sync that failed', async () => {
    const failure = new Error('down');
    syncIndex.mockRejectedValue(failure);
    const { result } = renderHook(() => useIndexSync(onSynced));

    await act(() => result.current.sync());

    expect(result.current.syncError).toBe(failure);
    expect(result.current.synced).toBeNull();
    expect(onSynced).not.toHaveBeenCalled();
  });

  it('forgets the last result when it syncs again', async () => {
    const { result } = renderHook(() => useIndexSync(onSynced));
    await act(() => result.current.sync());
    syncIndex.mockRejectedValue(new Error('down'));

    await act(() => result.current.sync());

    expect(result.current.synced).toBeNull();
  });

  it('needs no callback', async () => {
    const { result } = renderHook(() => useIndexSync());

    await act(() => result.current.sync());

    expect(result.current.synced).toEqual({ indexed: 3, removed: 1, failed: 0 });
    expect(result.current.syncError).toBeNull();
  });
});
