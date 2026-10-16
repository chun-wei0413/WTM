// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { meme } from './memeFixtures';
import { useTileActions } from './useTileActions';

const h = vi.hoisted(() => ({ image: vi.fn(), saveBlob: vi.fn(), add: vi.fn(), remove: vi.fn(), favorites: new Set<string>() }));
vi.mock('../api/client', () => ({ api: { library: { image: h.image } } }));
vi.mock('../lib/download', () => ({ saveBlob: h.saveBlob, fileNameFor: (base: string) => `${base}.png` }));
vi.mock('../Favorites/FavoritesContext', () => ({
  useFavorites: () => ({ isFavorite: (id: string) => h.favorites.has(id), add: h.add, remove: h.remove }),
}));

beforeEach(() => {
  h.favorites = new Set();
  h.image.mockReset().mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
  h.saveBlob.mockReset();
  h.add.mockReset().mockResolvedValue(undefined);
  h.remove.mockReset().mockResolvedValue(undefined);
});
afterEach(cleanup);

describe('useTileActions', () => {
  it('knows whether the meme is a favorite', () => {
    h.favorites = new Set(['abcdef123456']);

    expect(renderHook(() => useTileActions(meme())).result.current.favorite).toBe(true);
    expect(renderHook(() => useTileActions(meme({ templateId: 'other' }))).result.current.favorite).toBe(false);
  });

  it('downloads the picture under a name made from the template id', async () => {
    const { result } = renderHook(() => useTileActions(meme()));

    await act(() => result.current.download());

    expect(h.image).toHaveBeenCalledWith('abcdef123456');
    expect(h.saveBlob).toHaveBeenCalledWith(expect.any(Blob), 'meme-abcdef12.png');
    expect(result.current.downloading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('says it is downloading meanwhile', async () => {
    let finish!: (blob: Blob) => void;
    h.image.mockReturnValue(new Promise<Blob>((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useTileActions(meme()));

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.download();
    });
    expect(result.current.downloading).toBe(true);

    await act(async () => {
      finish(new Blob(['x']));
      await pending;
    });
    expect(result.current.downloading).toBe(false);
  });

  it('keeps the error of a download that failed', async () => {
    const failure = new Error('gone');
    h.image.mockRejectedValue(failure);
    const { result } = renderHook(() => useTileActions(meme()));

    await act(() => result.current.download());

    expect(result.current.error).toBe(failure);
    expect(h.saveBlob).not.toHaveBeenCalled();
  });

  it('adds a meme that is not a favorite, and removes one that is', async () => {
    const item = meme();
    const { result } = renderHook(() => useTileActions(item));
    await act(() => result.current.toggleFavorite());
    expect(h.add).toHaveBeenCalledWith(item);

    h.favorites = new Set(['abcdef123456']);
    const again = renderHook(() => useTileActions(item));
    await act(() => again.result.current.toggleFavorite());
    expect(h.remove).toHaveBeenCalledWith('abcdef123456');
  });

  it('keeps the error when the server refuses, and forgets it on the next try', async () => {
    const failure = new Error('refused');
    h.add.mockRejectedValueOnce(failure);
    const { result } = renderHook(() => useTileActions(meme()));

    await act(() => result.current.toggleFavorite());
    expect(result.current.error).toBe(failure);
    expect(result.current.starring).toBe(false);

    await act(() => result.current.toggleFavorite());
    expect(result.current.error).toBeNull();
  });
});
