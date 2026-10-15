// @vitest-environment jsdom
import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { usePicture } from './usePicture';

const { image } = vi.hoisted(() => ({ image: vi.fn() }));
vi.mock('../api/client', () => ({ api: { library: { image } } }));

let revoked: string[];
let decode: () => Promise<void>;

beforeEach(() => {
  revoked = [];
  decode = async () => undefined;
  image.mockReset().mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
  vi.stubGlobal(
    'Image',
    class {
      src = '';
      naturalWidth = 640;
      naturalHeight = 480;
      decode() {
        return decode();
      }
    },
  );
  URL.createObjectURL = () => 'blob:picture-1';
  URL.revokeObjectURL = (url: string) => void revoked.push(url);
});
afterEach(cleanup);

describe('usePicture', () => {
  it('is loading until the picture is fetched and decoded, then knows its size and type', async () => {
    const { result } = renderHook(() => usePicture('t-1'));

    expect(result.current).toEqual({ state: 'loading' });
    await waitFor(() => expect(result.current.state).toBe('ready'));
    expect(result.current).toMatchObject({ state: 'ready', url: 'blob:picture-1', size: { width: 640, height: 480 }, type: 'image/png' });
    expect(image).toHaveBeenCalledWith('t-1');
  });

  it('keeps the error when the picture cannot be fetched', async () => {
    const failure = new Error('gone');
    image.mockRejectedValue(failure);

    const { result } = renderHook(() => usePicture('t-1'));

    await waitFor(() => expect(result.current).toEqual({ state: 'error', error: failure }));
  });

  it('keeps the error when the picture cannot be decoded', async () => {
    const failure = new Error('not an image');
    decode = () => Promise.reject(failure);

    const { result } = renderHook(() => usePicture('t-1'));

    await waitFor(() => expect(result.current).toEqual({ state: 'error', error: failure }));
  });

  it('hands the picture back to the browser when it is no longer shown', async () => {
    const { result, unmount } = renderHook(() => usePicture('t-1'));
    await waitFor(() => expect(result.current.state).toBe('ready'));

    unmount();

    expect(revoked).toEqual(['blob:picture-1']);
  });

  it('does not show a picture that arrives after the visitor has moved on', async () => {
    let finish!: (blob: Blob) => void;
    image.mockReturnValue(new Promise<Blob>((resolve) => (finish = resolve)));
    const { result, unmount } = renderHook(() => usePicture('t-1'));
    unmount();

    finish(new Blob(['late']));
    await new Promise((resolve) => setTimeout(resolve, 20));

    expect(result.current).toEqual({ state: 'loading' });
  });

  it('stays loading when there is no template to load', () => {
    const { result } = renderHook(() => usePicture(null));

    expect(result.current).toEqual({ state: 'loading' });
    expect(image).not.toHaveBeenCalled();
  });
});
