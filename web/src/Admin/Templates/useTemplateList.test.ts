// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { manySummaries, summary } from './templateListFixtures';
import { PAGE, useTemplateList } from './useTemplateList';

const { listTemplates } = vi.hoisted(() => ({ listTemplates: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { listTemplates } } }));

beforeEach(() => {
  listTemplates.mockReset().mockResolvedValue(manySummaries(9));
});
afterEach(cleanup);

async function loaded() {
  const hook = renderHook(() => useTemplateList());
  await waitFor(() => expect(hook.result.current.load.state).toBe('ready'));
  return hook;
}

describe('useTemplateList', () => {
  it('is loading, then lists the approved templates first', async () => {
    const { result } = renderHook(() => useTemplateList());
    expect(result.current.load).toEqual({ state: 'loading' });
    expect(result.current.matching).toEqual([]);

    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    expect(result.current.status).toBe('APPROVED');
    expect(result.current.matching.map((t) => t.status)).toEqual(['APPROVED', 'APPROVED', 'APPROVED']);
    expect(result.current.counts).toEqual({ APPROVED: 3, DRAFT: 3, RETIRED: 3, ALL: 9 });
  });

  it('keeps the error when the list cannot be read', async () => {
    const failure = new Error('down');
    listTemplates.mockRejectedValue(failure);

    const { result } = renderHook(() => useTemplateList());

    await waitFor(() => expect(result.current.load).toEqual({ state: 'error', error: failure }));
  });

  it('reads the list only once, and answers a tab or a search from it', async () => {
    const { result } = await loaded();

    act(() => result.current.choose('DRAFT'));
    expect(result.current.matching.every((t) => t.status === 'DRAFT')).toBe(true);
    act(() => result.current.choose('ALL'));
    expect(result.current.matching).toHaveLength(9);
    act(() => result.current.search('meme 4'));

    expect(result.current.matching.map((t) => t.name)).toEqual(['Meme 4']);
    expect(listTemplates).toHaveBeenCalledTimes(1);
  });

  it('searches the meaning and the tags too', async () => {
    listTemplates.mockResolvedValue([
      summary(1, 'APPROVED', { meaning: 'a dog in a fire' }),
      summary(2, 'APPROVED', { tags: ['cat'] }),
    ]);
    const { result } = await loaded();

    act(() => result.current.search('fire'));
    expect(result.current.matching.map((t) => t.id)).toEqual(['t-1']);
    act(() => result.current.search('cat'));
    expect(result.current.matching.map((t) => t.id)).toEqual(['t-2']);
  });

  it('shows a page of the matches, and a page more each time it is asked', async () => {
    listTemplates.mockResolvedValue(Array.from({ length: PAGE * 2 + 5 }, (_, i) => summary(i)));
    const { result } = await loaded();
    expect(result.current.shown).toBe(PAGE);

    act(() => result.current.showMore());
    expect(result.current.shown).toBe(PAGE * 2);
    act(() => result.current.showMore());

    expect(result.current.shown).toBe(PAGE * 3);
  });

  it('goes back to the first page when the tab or the search changes', async () => {
    const { result } = await loaded();
    act(() => result.current.showMore());
    expect(result.current.shown).toBe(PAGE * 2);

    act(() => result.current.choose('DRAFT'));
    expect(result.current.shown).toBe(PAGE);

    act(() => result.current.showMore());
    act(() => result.current.search('x'));
    expect(result.current.shown).toBe(PAGE);
  });

  it('does not show an answer that arrives after the page is gone', async () => {
    let finish!: (value: unknown) => void;
    listTemplates.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { result, unmount } = renderHook(() => useTemplateList());
    unmount();

    finish(manySummaries(3));
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(result.current.load).toEqual({ state: 'loading' });
  });
});
