// @vitest-environment jsdom
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { REPORT_POLL_MS } from '../../lib/reports';
import { automaticEntry, doneReview, reportCase } from './reportFixtures';
import { useReports } from './useReports';

const { list, automatic } = vi.hoisted(() => ({ list: vi.fn(), automatic: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { reports: { list, automatic } } } }));

beforeEach(() => {
  list.mockReset().mockResolvedValue([]);
  automatic.mockReset().mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('useReports', () => {
  it('is loading, then has the cases and what was decided automatically', async () => {
    list.mockResolvedValue([reportCase()]);
    automatic.mockResolvedValue([automaticEntry()]);

    const { result } = renderHook(() => useReports());

    expect(result.current.load).toEqual({ state: 'loading' });
    await waitFor(() => expect(result.current.load).toEqual({ state: 'ready', cases: [reportCase()], automatic: [automaticEntry()] }));
  });

  it('keeps the error when nothing could be read', async () => {
    const failure = new Error('down');
    list.mockRejectedValue(failure);

    const { result } = renderHook(() => useReports());

    await waitFor(() => expect(result.current.load).toEqual({ state: 'error', error: failure }));
  });

  it('keeps showing what it has when a later refresh fails', async () => {
    list.mockResolvedValueOnce([reportCase()]).mockRejectedValue(new Error('down'));
    const { result } = renderHook(() => useReports());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.load.state).toBe('ready');
  });

  it('reads again when asked', async () => {
    list.mockResolvedValueOnce([]).mockResolvedValueOnce([reportCase({ name: 'Fresh' })]);
    const { result } = renderHook(() => useReports());
    await waitFor(() => expect(result.current.load.state).toBe('ready'));

    await act(() => result.current.refresh());

    expect(result.current.load).toMatchObject({ cases: [{ name: 'Fresh' }] });
  });

  it('looks again every few seconds while the model is working, and stops when it is done', async () => {
    vi.useFakeTimers();
    const working = reportCase({ review: { status: 'RUNNING', suggestion: null, error: null } });
    list.mockResolvedValueOnce([working]).mockResolvedValueOnce([working]).mockResolvedValue([reportCase({ review: doneReview() })]);

    const { result } = renderHook(() => useReports());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(list).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(REPORT_POLL_MS);
    });
    expect(list).toHaveBeenCalledTimes(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(REPORT_POLL_MS);
    });
    expect(list).toHaveBeenCalledTimes(3);
    expect(result.current.load).toMatchObject({ state: 'ready', cases: [{ review: { status: 'DONE' } }] });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(REPORT_POLL_MS * 3);
    });
    expect(list).toHaveBeenCalledTimes(3); // nobody is working any more, so it stopped asking
  });

  it('does not poll when no case is being reviewed', async () => {
    vi.useFakeTimers();
    list.mockResolvedValue([reportCase({ review: null })]);

    renderHook(() => useReports());
    await act(async () => {
      await vi.advanceTimersByTimeAsync(REPORT_POLL_MS * 3);
    });

    expect(list).toHaveBeenCalledTimes(1);
  });
});
