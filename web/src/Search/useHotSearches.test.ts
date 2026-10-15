// @vitest-environment jsdom
import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useHotSearches } from './useHotSearches';

const { hotSearches } = vi.hoisted(() => ({ hotSearches: vi.fn() }));
vi.mock('../api/client', () => ({ api: { library: { hotSearches } } }));

beforeEach(() => {
  hotSearches.mockReset();
});
afterEach(cleanup);

describe('useHotSearches', () => {
  it('is empty until the terms arrive, then lists them', async () => {
    hotSearches.mockResolvedValue([
      { term: '黑人問號', searches: 5 },
      { term: '下班', searches: 3 },
    ]);

    const { result } = renderHook(() => useHotSearches());

    expect(result.current).toEqual([]);
    await waitFor(() => expect(result.current).toEqual(['黑人問號', '下班']));
    expect(hotSearches).toHaveBeenCalledWith(8);
  });

  it('stays empty when the terms cannot be fetched', async () => {
    hotSearches.mockRejectedValue(new Error('down'));

    const { result } = renderHook(() => useHotSearches());
    await waitFor(() => expect(hotSearches).toHaveBeenCalled());

    expect(result.current).toEqual([]);
  });
});
