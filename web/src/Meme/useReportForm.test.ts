// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import type { FormEvent } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MAX_COMMENT_LENGTH } from '../lib/reports';
import { useReportForm } from './useReportForm';

const { submitReport } = vi.hoisted(() => ({ submitReport: vi.fn() }));
vi.mock('../api/client', () => ({ api: { reports: { submit: submitReport } } }));

const onSent = vi.fn();
const event = () => ({ preventDefault: vi.fn() }) as unknown as FormEvent;

beforeEach(() => {
  submitReport.mockReset().mockResolvedValue(undefined);
  onSent.mockReset();
});
afterEach(cleanup);

describe('useReportForm', () => {
  it('starts with wrong tags and no comment', () => {
    const { result } = renderHook(() => useReportForm('t-1', onSent));

    expect(result.current).toMatchObject({ reason: 'WRONG_TAGS', comment: '', sending: false, error: null, tooLong: false });
  });

  it('sends the reason and the trimmed comment, and tells the card it went through', async () => {
    const { result } = renderHook(() => useReportForm('t-1', onSent));
    act(() => {
      result.current.setReason('NOT_A_MEME');
      result.current.setComment('  just a photo  ');
    });
    const e = event();

    await act(() => result.current.submit(e));

    expect(e.preventDefault).toHaveBeenCalled();
    expect(submitReport).toHaveBeenCalledWith('t-1', 'NOT_A_MEME', 'just a photo');
    expect(onSent).toHaveBeenCalledTimes(1);
  });

  it('counts a comment by characters, so an emoji is one', () => {
    const { result } = renderHook(() => useReportForm('t-1', onSent));

    act(() => result.current.setComment('😀'.repeat(MAX_COMMENT_LENGTH)));
    expect(result.current.tooLong).toBe(false);

    act(() => result.current.setComment('😀'.repeat(MAX_COMMENT_LENGTH + 1)));
    expect(result.current.tooLong).toBe(true);
  });

  it('does not send a comment that is too long', async () => {
    const { result } = renderHook(() => useReportForm('t-1', onSent));
    act(() => result.current.setComment('x'.repeat(MAX_COMMENT_LENGTH + 1)));

    await act(() => result.current.submit(event()));

    expect(submitReport).not.toHaveBeenCalled();
    expect(onSent).not.toHaveBeenCalled();
  });

  it('keeps the error of a report that failed, and lets the user send it again', async () => {
    const failure = new Error('down');
    submitReport.mockRejectedValueOnce(failure);
    const { result } = renderHook(() => useReportForm('t-1', onSent));

    await act(() => result.current.submit(event()));

    expect(result.current.error).toBe(failure);
    expect(result.current.sending).toBe(false);
    expect(onSent).not.toHaveBeenCalled();

    await act(() => result.current.submit(event()));
    expect(result.current.error).toBeNull();
    expect(onSent).toHaveBeenCalledTimes(1);
  });

  it('says it is sending meanwhile', async () => {
    let finish!: (value: unknown) => void;
    submitReport.mockReturnValue(new Promise((resolve) => (finish = resolve)));
    const { result } = renderHook(() => useReportForm('t-1', onSent));

    let pending!: Promise<void>;
    act(() => {
      pending = result.current.submit(event());
    });
    expect(result.current.sending).toBe(true);

    await act(async () => {
      finish(undefined);
      await pending;
    });
  });
});
