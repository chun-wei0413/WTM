// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { TextBox } from '../lib/memeBoxes';
import type { Picture } from './usePicture';
import { useMemeDownload } from './useMemeDownload';

const { renderMeme, saveBlob } = vi.hoisted(() => ({ renderMeme: vi.fn(), saveBlob: vi.fn() }));
vi.mock('../lib/renderMeme', () => ({ renderMeme }));
vi.mock('../lib/download', () => ({ saveBlob }));

const ready = { state: 'ready', url: 'blob:x', image: {} as HTMLImageElement, size: { width: 400, height: 300 }, type: 'image/png' } satisfies Picture;
const boxes = [{ id: 1, text: 'hi' }] as TextBox[];

beforeEach(() => {
  renderMeme.mockReset().mockResolvedValue(new Blob(['png']));
  saveBlob.mockReset();
});
afterEach(cleanup);

describe('useMemeDownload', () => {
  it('draws the meme and saves it under a name made from the template id', async () => {
    const { result } = renderHook(() => useMemeDownload(ready, boxes, 'abcdef123456'));

    await act(() => result.current.download());

    expect(renderMeme).toHaveBeenCalledWith(ready.image, ready.size, boxes);
    expect(saveBlob).toHaveBeenCalledWith(expect.any(Blob), 'my-meme-abcdef12.png');
    expect(result.current.error).toBeNull();
    expect(result.current.saving).toBe(false);
  });

  it('says it is saving while the meme is drawn', async () => {
    let finish!: (blob: Blob) => void;
    renderMeme.mockReturnValue(new Promise<Blob>((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useMemeDownload(ready, boxes, 'abcdef123456'));

    let saving!: Promise<void>;
    act(() => {
      saving = result.current.download();
    });
    expect(result.current.saving).toBe(true);

    await act(async () => {
      finish(new Blob(['png']));
      await saving;
    });
    expect(result.current.saving).toBe(false);
  });

  it('keeps the error of a meme that could not be drawn, and saves nothing', async () => {
    const failure = new Error('canvas is full');
    renderMeme.mockRejectedValue(failure);
    const { result } = renderHook(() => useMemeDownload(ready, boxes, 'abcdef123456'));

    await act(() => result.current.download());

    expect(result.current.error).toBe(failure);
    expect(saveBlob).not.toHaveBeenCalled();
    expect(result.current.saving).toBe(false);
  });

  it('forgets an old error when the visitor tries again', async () => {
    renderMeme.mockRejectedValueOnce(new Error('first'));
    const { result } = renderHook(() => useMemeDownload(ready, boxes, 'abcdef123456'));
    await act(() => result.current.download());

    await act(() => result.current.download());

    expect(result.current.error).toBeNull();
    expect(saveBlob).toHaveBeenCalledTimes(1);
  });

  it('does nothing until the picture is ready', async () => {
    const { result } = renderHook(() => useMemeDownload({ state: 'loading' }, boxes, 'abcdef123456'));

    await act(() => result.current.download());

    expect(renderMeme).not.toHaveBeenCalled();
    expect(result.current.saving).toBe(false);
  });
});
