// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import type { FormEvent } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAddUrl } from './useAddUrl';

const { addUrl } = vi.hoisted(() => ({ addUrl: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { collection: { addUrl } } } }));

const submitEvent = () => ({ preventDefault: vi.fn() }) as unknown as FormEvent;

function filled(url = 'https://example.com/a.jpg', title = '') {
  const hook = renderHook(() => useAddUrl());
  act(() => {
    hook.result.current.setUrl(url);
    hook.result.current.setTitle(title);
  });
  return hook;
}

beforeEach(() => {
  addUrl.mockReset().mockResolvedValue({ status: 'IMPORTED', templateId: 't', reason: null, fileName: 'a.jpg' });
});
afterEach(cleanup);

describe('useAddUrl', () => {
  it('only accepts a web address', () => {
    expect(filled('not a url').result.current.valid).toBe(false);
    expect(filled('ftp://example.com/a.jpg').result.current.valid).toBe(false);
    expect(filled('https://example.com/a.jpg').result.current.valid).toBe(true);
  });

  it('does not send an address that is not valid', async () => {
    const { result } = filled('not a url');

    await act(() => result.current.submit(submitEvent()));

    expect(addUrl).not.toHaveBeenCalled();
  });

  it('keeps the browser from submitting the form itself', async () => {
    const { result } = filled();
    const event = submitEvent();

    await act(() => result.current.submit(event));

    expect(event.preventDefault).toHaveBeenCalled();
  });

  it('sends the trimmed address and name, then empties the fields and says it is in', async () => {
    const { result } = filled('  https://example.com/a.jpg ', ' my meme ');

    await act(() => result.current.submit(submitEvent()));

    expect(addUrl).toHaveBeenCalledWith('https://example.com/a.jpg', 'my meme');
    expect(result.current.message).toEqual({ kind: 'success', text: '已加入圖庫,稍後會自動標記。' });
    expect(result.current.url).toBe('');
    expect(result.current.title).toBe('');
    expect(result.current.busy).toBe(false);
  });

  it('sends no name when the field was left empty', async () => {
    const { result } = filled('https://example.com/a.jpg', '   ');

    await act(() => result.current.submit(submitEvent()));

    expect(addUrl).toHaveBeenCalledWith('https://example.com/a.jpg', undefined);
  });

  it('says the picture is already in the library, and keeps the fields', async () => {
    addUrl.mockResolvedValue({ status: 'DUPLICATE', templateId: 't', reason: null, fileName: 'a.jpg' });
    const { result } = filled();

    await act(() => result.current.submit(submitEvent()));

    expect(result.current.message).toEqual({ kind: 'info', text: '這張圖已經在圖庫裡了。' });
    expect(result.current.url).toBe('https://example.com/a.jpg');
  });

  it('says why the address cannot be used, with the reason when there is one', async () => {
    addUrl.mockResolvedValueOnce({ status: 'REJECTED', templateId: null, reason: 'not an image', fileName: 'a' });
    const { result } = filled();
    await act(() => result.current.submit(submitEvent()));
    expect(result.current.message).toEqual({ kind: 'warn', text: '這個網址的圖片不能用:not an image' });

    addUrl.mockResolvedValueOnce({ status: 'REJECTED', templateId: null, reason: null, fileName: 'a' });
    await act(() => result.current.submit(submitEvent()));
    expect(result.current.message).toEqual({ kind: 'warn', text: '這個網址的圖片不能用' });
  });

  it('keeps the error of a request that failed', async () => {
    const failure = new Error('down');
    addUrl.mockRejectedValue(failure);
    const { result } = filled();

    await act(() => result.current.submit(submitEvent()));

    expect(result.current.error).toBe(failure);
    expect(result.current.message).toBeNull();
    expect(result.current.busy).toBe(false);
  });

  it('says it is busy while it sends', async () => {
    let finish!: (value: unknown) => void;
    addUrl.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { result } = filled();

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.submit(submitEvent());
    });
    expect(result.current.busy).toBe(true);

    await act(async () => {
      finish({ status: 'DUPLICATE', templateId: null, reason: null, fileName: 'a' });
      await pending;
    });
    expect(result.current.busy).toBe(false);
  });
});
