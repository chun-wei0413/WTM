// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PickResult } from '../api/types';
import { usePick } from './usePick';

const { pick } = vi.hoisted(() => ({ pick: vi.fn() }));
vi.mock('../api/client', () => ({ api: { pick } }));

function answer(reason: string): PickResult {
  return { chosen: null, reason, others: [] };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

beforeEach(() => {
  pick.mockReset();
});
afterEach(cleanup);

describe('usePick', () => {
  it('starts idle', () => {
    const { result } = renderHook(() => usePick());

    expect(result.current.picked).toEqual({ state: 'idle' });
  });

  it('asks for the trimmed situation, is loading meanwhile, and then shows the answer', async () => {
    const waiting = deferred<PickResult>();
    pick.mockReturnValue(waiting.promise);
    const { result } = renderHook(() => usePick());

    let call!: Promise<void>;
    act(() => {
      call = result.current.pickFor('  同事一直問進度  ');
    });
    expect(result.current.picked).toEqual({ state: 'loading' });
    expect(pick).toHaveBeenCalledWith('同事一直問進度');

    await act(async () => {
      waiting.resolve(answer('fits'));
      await call;
    });
    expect(result.current.picked).toEqual({ state: 'ready', result: answer('fits') });
  });

  it('ignores a blank situation', async () => {
    const { result } = renderHook(() => usePick());

    await act(() => result.current.pickFor('   '));

    expect(pick).not.toHaveBeenCalled();
    expect(result.current.picked).toEqual({ state: 'idle' });
  });

  it('keeps the newest answer when an older, slower request finishes last', async () => {
    const slow = deferred<PickResult>();
    pick.mockImplementation((s: string) => (s === 'old' ? slow.promise : Promise.resolve(answer('new'))));
    const { result } = renderHook(() => usePick());

    let older!: Promise<void>;
    act(() => {
      older = result.current.pickFor('old');
    });
    await act(() => result.current.pickFor('new'));
    await act(async () => {
      slow.resolve(answer('old'));
      await older;
    });

    expect(result.current.picked).toEqual({ state: 'ready', result: answer('new') });
  });

  it('shows the error of a failed request', async () => {
    const failure = new Error('model is down');
    pick.mockRejectedValue(failure);
    const { result } = renderHook(() => usePick());

    await act(() => result.current.pickFor('anything'));

    expect(result.current.picked).toEqual({ state: 'error', error: failure });
  });

  it('goes back to idle when cleared', async () => {
    pick.mockResolvedValue(answer('fits'));
    const { result } = renderHook(() => usePick());
    await act(() => result.current.pickFor('anything'));

    act(() => result.current.clear());

    expect(result.current.picked).toEqual({ state: 'idle' });
  });
});
