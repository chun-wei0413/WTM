// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LibraryItem } from '../api/types';
import { useSearch } from './useSearch';

const { random, search } = vi.hoisted(() => ({ random: vi.fn(), search: vi.fn() }));
vi.mock('../api/client', () => ({ api: { search, library: { random } } }));

function meme(name: string): LibraryItem {
  return { templateId: name, name } as LibraryItem;
}

/** A request whose answer the test hands out later, so two requests can finish in the wrong order. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

beforeEach(() => {
  random.mockReset();
  search.mockReset();
  random.mockResolvedValue([meme('random-1')]);
});

afterEach(cleanup);

describe('useSearch', () => {
  it('shows random memes to begin with', async () => {
    const { result } = renderHook(() => useSearch());

    expect(result.current.load).toEqual({ state: 'loading' });
    await waitFor(() => expect(result.current.load).toEqual({ state: 'ready', items: [meme('random-1')] }));
    expect(result.current.searched).toBeNull();
    expect(random).toHaveBeenCalledWith(12);
  });

  it('shows the answer to a search and remembers what was searched, trimmed', async () => {
    search.mockResolvedValue([meme('found')]);
    const { result } = renderHook(() => useSearch());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    await act(() => result.current.search('  老闆又改需求  '));

    expect(search).toHaveBeenCalledWith('老闆又改需求', 12);
    expect(result.current.searched).toBe('老闆又改需求');
    expect(result.current.load).toEqual({ state: 'ready', items: [meme('found')] });
  });

  it('ignores a blank search', async () => {
    const { result } = renderHook(() => useSearch());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    await act(() => result.current.search('   '));

    expect(search).not.toHaveBeenCalled();
    expect(result.current.searched).toBeNull();
  });

  it('keeps the newest answer when an older, slower request finishes last', async () => {
    const slow = deferred<LibraryItem[]>();
    search.mockImplementation((q: string) => (q === 'old' ? slow.promise : Promise.resolve([meme('new-answer')])));
    const { result } = renderHook(() => useSearch());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    let older!: Promise<void>;
    act(() => {
      older = result.current.search('old');
    });
    await act(() => result.current.search('new'));
    await act(async () => {
      slow.resolve([meme('old-answer')]);
      await older;
    });

    expect(result.current.searched).toBe('new');
    expect(result.current.load).toEqual({ state: 'ready', items: [meme('new-answer')] });
  });

  it('shows the error of a failed search, and not the one of an older request', async () => {
    const failure = new Error('down');
    search.mockRejectedValue(failure);
    const { result } = renderHook(() => useSearch());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    await act(() => result.current.search('anything'));

    expect(result.current.load).toEqual({ state: 'error', error: failure });
  });

  it('goes back to random memes and forgets the search', async () => {
    search.mockResolvedValue([meme('found')]);
    const { result } = renderHook(() => useSearch());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));
    await act(() => result.current.search('x'));

    await act(() => result.current.showRandom());

    expect(result.current.searched).toBeNull();
    expect(result.current.load).toEqual({ state: 'ready', items: [meme('random-1')] });
    expect(random).toHaveBeenCalledTimes(2);
  });

  it('does not show an answer that arrives after the page is gone', async () => {
    const late = deferred<LibraryItem[]>();
    random.mockReturnValue(late.promise);
    const { result, unmount } = renderHook(() => useSearch());
    unmount();

    await act(async () => {
      late.resolve([meme('too-late')]);
      await late.promise;
    });

    expect(result.current.load).toEqual({ state: 'loading' });
  });
});
