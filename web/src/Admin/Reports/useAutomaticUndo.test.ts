// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAutomaticUndo } from './useAutomaticUndo';

const { undo } = vi.hoisted(() => ({ undo: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { reports: { undo } } } }));

const onChanged = vi.fn();

beforeEach(() => {
  undo.mockReset().mockResolvedValue(undefined);
  onChanged.mockReset();
});
afterEach(cleanup);

describe('useAutomaticUndo', () => {
  it('takes the decision back and tells the page to read again', async () => {
    const { result } = renderHook(() => useAutomaticUndo(onChanged));

    await act(() => result.current.undo('t-9'));

    expect(undo).toHaveBeenCalledWith('t-9');
    expect(onChanged).toHaveBeenCalledTimes(1);
    expect(result.current.busy).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('names the template being taken back', async () => {
    let finish!: () => void;
    undo.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useAutomaticUndo(onChanged));

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.undo('t-9');
    });
    expect(result.current.busy).toBe('t-9');

    await act(async () => {
      finish();
      await pending;
    });
    expect(result.current.busy).toBeNull();
  });

  it('keeps the error when it cannot be taken back', async () => {
    const failure = new Error('too late');
    undo.mockRejectedValue(failure);
    const { result } = renderHook(() => useAutomaticUndo(onChanged));

    await act(() => result.current.undo('t-9'));

    expect(result.current.error).toBe(failure);
    expect(onChanged).not.toHaveBeenCalled();
  });
});
