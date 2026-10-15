// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import type { ChangeEvent, FormEvent, ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useNewTemplate } from './useNewTemplate';

const { draftTemplate } = vi.hoisted(() => ({ draftTemplate: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { draftTemplate } } }));

let where = '';
function Location() {
  where = useLocation().pathname;
  return null;
}
function wrapper({ children }: { children: ReactNode }) {
  return (
    <MemoryRouter initialEntries={['/admin/templates']}>
      <Routes>
        <Route path="*" element={<Location />} />
      </Routes>
      {children}
    </MemoryRouter>
  );
}

const chosen = (file: File | null) => ({ target: { files: file ? [file] : [] } }) as unknown as ChangeEvent<HTMLInputElement>;
const submit = () => ({ preventDefault: vi.fn() }) as unknown as FormEvent;
const png = (name: string) => new File(['x'], name, { type: 'image/png' });

let revoked: string[];

beforeEach(() => {
  where = '';
  revoked = [];
  let n = 0;
  URL.createObjectURL = () => `blob:preview-${++n}`;
  URL.revokeObjectURL = (url: string) => void revoked.push(url);
  draftTemplate.mockReset().mockResolvedValue({ id: 'new-1' });
});
afterEach(cleanup);

describe('useNewTemplate', () => {
  it('starts empty', () => {
    const { result } = renderHook(() => useNewTemplate(), { wrapper });

    expect(result.current).toMatchObject({ name: '', file: null, preview: null, creating: false, createError: null });
  });

  it('shows a preview of the chosen picture and names the template after its file', () => {
    const { result } = renderHook(() => useNewTemplate(), { wrapper });

    act(() => result.current.onFile(chosen(png('my picture.final.png'))));

    expect(result.current.name).toBe('my picture.final');
    expect(result.current.preview).toBe('blob:preview-1');
  });

  it('keeps a name that was already typed', () => {
    const { result } = renderHook(() => useNewTemplate(), { wrapper });
    act(() => result.current.setName('Mine'));

    act(() => result.current.onFile(chosen(png('other.png'))));

    expect(result.current.name).toBe('Mine');
  });

  it('hands the preview back when the picture is replaced or removed', () => {
    const { result } = renderHook(() => useNewTemplate(), { wrapper });
    act(() => result.current.onFile(chosen(png('a.png'))));

    act(() => result.current.onFile(chosen(png('b.png'))));
    expect(revoked).toEqual(['blob:preview-1']);

    act(() => result.current.onFile(chosen(null)));
    expect(revoked).toEqual(['blob:preview-1', 'blob:preview-2']);
    expect(result.current.preview).toBeNull();
  });

  it('does nothing without a picture', async () => {
    const { result } = renderHook(() => useNewTemplate(), { wrapper });
    act(() => result.current.setName('Name'));

    await act(() => result.current.create(submit()));

    expect(draftTemplate).not.toHaveBeenCalled();
  });

  it('sends the trimmed name and the picture, then opens the editor of the new draft', async () => {
    const { result } = renderHook(() => useNewTemplate(), { wrapper });
    const file = png('a.png');
    act(() => result.current.onFile(chosen(file)));
    act(() => result.current.setName('  Drake  '));
    const event = submit();

    await act(() => result.current.create(event));

    expect(event.preventDefault).toHaveBeenCalled();
    expect(draftTemplate).toHaveBeenCalledWith('Drake', file);
    expect(where).toBe('/admin/templates/new-1');
  });

  it('keeps the error of a draft that could not be created, and lets the visitor try again', async () => {
    const failure = new Error('too big');
    draftTemplate.mockRejectedValueOnce(failure);
    const { result } = renderHook(() => useNewTemplate(), { wrapper });
    act(() => result.current.onFile(chosen(png('a.png'))));

    await act(() => result.current.create(submit()));

    expect(result.current.createError).toBe(failure);
    expect(result.current.creating).toBe(false);
    expect(where).toBe('/admin/templates');

    await act(() => result.current.create(submit()));
    expect(result.current.createError).toBeNull();
    expect(where).toBe('/admin/templates/new-1');
  });

  it('says it is creating meanwhile', async () => {
    let finish!: (value: unknown) => void;
    draftTemplate.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useNewTemplate(), { wrapper });
    act(() => result.current.onFile(chosen(png('a.png'))));

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.create(submit());
    });
    expect(result.current.creating).toBe(true);

    await act(async () => {
      finish({ id: 'x' });
      await pending;
    });
  });
});
