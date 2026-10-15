// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import type { ChangeEvent } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { IngestResult } from '../../api/types';
import { UPLOAD_BATCH, useUpload } from './useUpload';

const { addFiles } = vi.hoisted(() => ({ addFiles: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { collection: { addFiles } } } }));

const png = (name: string) => new File(['x'], name, { type: 'image/png' });
const text = (name: string) => new File(['x'], name, { type: 'text/plain' });

/** What a file input hands over when the visitor has chosen files. */
function chosen(files: File[]) {
  const input = { files, value: 'C:\\fakepath\\x' };
  return { event: { target: input } as unknown as ChangeEvent<HTMLInputElement>, input };
}

function imported(files: File[]): IngestResult[] {
  return files.map((f) => ({ fileName: f.name, status: 'IMPORTED', templateId: 't', reason: null }));
}

beforeEach(() => {
  addFiles.mockReset().mockImplementation(async (files: File[]) => imported(files));
});
afterEach(cleanup);

describe('useUpload', () => {
  it('sends the pictures and sums up what the library did', async () => {
    const { result } = renderHook(() => useUpload());
    const { event } = chosen([png('a.png'), png('b.png')]);

    await act(() => result.current.upload(event));

    expect(addFiles).toHaveBeenCalledTimes(1);
    expect(result.current.summary).toMatchObject({ imported: 2 });
    expect(result.current.uploading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('clears the input, so the same folder can be chosen again', async () => {
    const { result } = renderHook(() => useUpload());
    const { event, input } = chosen([png('a.png')]);

    await act(() => result.current.upload(event));

    expect(input.value).toBe('');
  });

  it('sends a big folder in batches and counts the progress', async () => {
    const files = Array.from({ length: UPLOAD_BATCH * 2 + 5 }, (_, i) => png(`p${i}.png`));
    const progress: number[] = [];
    let pending: Promise<void> | undefined;
    addFiles.mockImplementation(async (batch: File[]) => {
      progress.push(batch.length);
      return imported(batch);
    });
    const { result } = renderHook(() => useUpload());

    await act(async () => {
      pending = result.current.upload(chosen(files).event);
      await pending;
    });

    expect(progress).toEqual([UPLOAD_BATCH, UPLOAD_BATCH, 5]);
    expect(result.current.progress).toEqual({ done: files.length, total: files.length });
    expect(result.current.summary).toMatchObject({ imported: files.length });
  });

  it('says it is uploading meanwhile', async () => {
    let finish!: (results: IngestResult[]) => void;
    addFiles.mockReturnValue(new Promise<IngestResult[]>((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useUpload());

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.upload(chosen([png('a.png')]).event);
    });
    expect(result.current.uploading).toBe(true);
    expect(result.current.progress).toEqual({ done: 0, total: 1 });

    await act(async () => {
      finish(imported([png('a.png')]));
      await pending;
    });
    expect(result.current.uploading).toBe(false);
  });

  it('leaves out what is not a picture, and counts it', async () => {
    const { result } = renderHook(() => useUpload());

    await act(() => result.current.upload(chosen([png('a.png'), text('notes.txt')]).event));

    expect(addFiles.mock.calls[0]?.[0]).toHaveLength(1);
    expect(result.current.skipped).toBe(1);
  });

  it('sends nothing when no picture was chosen', async () => {
    const { result } = renderHook(() => useUpload());

    await act(() => result.current.upload(chosen([text('notes.txt')]).event));

    expect(addFiles).not.toHaveBeenCalled();
    expect(result.current.skipped).toBe(1);
    expect(result.current.summary).toBeNull();
    expect(result.current.uploading).toBe(false);
  });

  it('shows what did get in when a later batch fails', async () => {
    const failure = new Error('second batch refused');
    const files = Array.from({ length: UPLOAD_BATCH + 1 }, (_, i) => png(`p${i}.png`));
    addFiles.mockImplementationOnce(async (batch: File[]) => imported(batch)).mockRejectedValueOnce(failure);
    const { result } = renderHook(() => useUpload());

    await act(() => result.current.upload(chosen(files).event));

    expect(result.current.error).toBe(failure);
    expect(result.current.summary).toMatchObject({ imported: UPLOAD_BATCH });
    expect(result.current.uploading).toBe(false);
  });

  it('forgets the last result when new pictures are chosen', async () => {
    const { result } = renderHook(() => useUpload());
    await act(() => result.current.upload(chosen([png('a.png')]).event));

    await act(() => result.current.upload(chosen([]).event));

    expect(result.current.summary).toBeNull();
    expect(result.current.skipped).toBe(0);
  });
});
