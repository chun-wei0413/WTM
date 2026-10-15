// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { doneReview, reportCase } from './reportFixtures';
import { useCaseActions } from './useCaseActions';

const reports = vi.hoisted(() => ({ apply: vi.fn(), dismiss: vi.fn(), reanalyze: vi.fn() }));
const retire = vi.hoisted(() => vi.fn());
vi.mock('../../api/client', () => ({ api: { admin: { reports, retire } } }));

const onChanged = vi.fn();

function actions(item = reportCase({ review: doneReview() })) {
  return renderHook(() => useCaseActions(item, onChanged));
}

beforeEach(() => {
  Object.values(reports).forEach((fn) => fn.mockReset().mockResolvedValue(undefined));
  retire.mockReset().mockResolvedValue(undefined);
  onChanged.mockReset();
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('useCaseActions', () => {
  it('applies the proposal and tells the page to read again', async () => {
    const { result } = actions();

    await act(async () => result.current.apply());

    expect(reports.apply).toHaveBeenCalledWith('t-1');
    expect(onChanged).toHaveBeenCalledTimes(1);
    expect(result.current.busy).toBeNull();
  });

  it('asks before applying the description of a picture the model says is no meme, and stops on no', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { result } = actions(reportCase({ review: doneReview({ isMeme: false }) }));

    await act(async () => result.current.apply());
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(reports.apply).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    await act(async () => result.current.apply());
    await vi.waitFor(() => expect(reports.apply).toHaveBeenCalledWith('t-1'));
  });

  it('does not ask before applying the proposal for a meme', async () => {
    const confirm = vi.spyOn(window, 'confirm');
    const { result } = actions();

    await act(async () => result.current.apply());

    expect(confirm).not.toHaveBeenCalled();
  });

  it('dismisses the reports', async () => {
    const { result } = actions();

    await act(async () => result.current.dismiss());

    expect(reports.dismiss).toHaveBeenCalledWith('t-1');
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it('has the model look again', async () => {
    const { result } = actions();

    await act(async () => result.current.reanalyze());

    expect(reports.reanalyze).toHaveBeenCalledWith('t-1');
  });

  it('retires the meme and closes its reports, but only after the administrator confirms', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { result } = actions();

    await act(async () => result.current.retire());
    expect(retire).not.toHaveBeenCalled();

    confirm.mockReturnValue(true);
    await act(async () => result.current.retire());
    await vi.waitFor(() => expect(reports.dismiss).toHaveBeenCalledWith('t-1'));
    expect(retire).toHaveBeenCalledWith('t-1');
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it('names the action in progress, so every button can be switched off', async () => {
    let finish!: () => void;
    reports.dismiss.mockReturnValue(new Promise<void>((resolve) => (finish = resolve)));
    const { result } = actions();

    act(() => result.current.dismiss());
    expect(result.current.busy).toBe('dismiss');

    await act(async () => finish());
    expect(result.current.busy).toBeNull();
  });

  it('keeps the error of an action that failed, and does not tell the page to read again', async () => {
    const failure = new Error('refused');
    reports.apply.mockRejectedValue(failure);
    const { result } = actions();

    await act(async () => result.current.apply());

    await vi.waitFor(() => expect(result.current.error).toBe(failure));
    expect(onChanged).not.toHaveBeenCalled();
    expect(result.current.busy).toBeNull();
  });

  it('forgets an old error when another action starts', async () => {
    reports.apply.mockRejectedValueOnce(new Error('first'));
    const { result } = actions();
    await act(async () => result.current.apply());
    await vi.waitFor(() => expect(result.current.error).not.toBeNull());

    await act(async () => result.current.dismiss());

    await vi.waitFor(() => expect(result.current.error).toBeNull());
  });
});
