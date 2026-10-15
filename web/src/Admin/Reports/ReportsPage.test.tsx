// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { automaticEntry, doneReview, reportCase } from './reportFixtures';
import { ReportsPage } from './ReportsPage';

const reports = vi.hoisted(() => ({ list: vi.fn(), automatic: vi.fn(), undo: vi.fn(), apply: vi.fn(), dismiss: vi.fn(), reanalyze: vi.fn() }));
const retire = vi.hoisted(() => vi.fn());
vi.mock('../../api/client', () => ({ api: { admin: { reports, retire } } }));
vi.mock('../../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));

function open() {
  return render(
    <MemoryRouter>
      <ReportsPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  Object.values(reports).forEach((fn) => fn.mockReset().mockResolvedValue(undefined));
  reports.list.mockResolvedValue([]);
  reports.automatic.mockResolvedValue([]);
  retire.mockReset().mockResolvedValue(undefined);
  vi.spyOn(window, 'confirm').mockReturnValue(true);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('ReportsPage', () => {
  it('says it is loading, then that nothing waits when there are no reports', async () => {
    open();
    expect(screen.getByText('載入中…')).toBeTruthy();

    expect(await screen.findByText('目前沒有待處理的回報。')).toBeTruthy();
  });

  it('shows the error when the reports cannot be read', async () => {
    reports.list.mockRejectedValue(new Error('down'));

    open();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('shows each reported meme with its complaints', async () => {
    reports.list.mockResolvedValue([reportCase()]);

    open();

    expect(await screen.findByText('Drake')).toBeTruthy();
    expect(screen.getByRole('heading', { name: '用戶回報(2)' })).toBeTruthy();
    expect(screen.getByText('標籤不精確')).toBeTruthy();
    expect(screen.getByText('用戶報告:tags are off')).toBeTruthy();
    expect(screen.getByText('(沒有補充說明)')).toBeTruthy();
    expect(screen.getByRole('link', { name: '開啟編輯頁' }).getAttribute('href')).toBe('/admin/templates/t-1');
  });

  it('explains why a case has not been analyzed yet, and lets the administrator ask by hand', async () => {
    reports.list.mockResolvedValue([reportCase({ review: null, weight: 1, neededWeight: 3 })]);
    open();

    expect(await screen.findByText(/目前回報的可信度合計 1 \/ 3/)).toBeTruthy();
    expect((screen.getByRole('button', { name: '套用 AI 建議' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: '重新分析' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows that the model is working, and does not let a second analysis start', async () => {
    reports.list.mockResolvedValue([reportCase({ review: { status: 'RUNNING', suggestion: null, error: null } })]);
    open();

    expect(await screen.findByText('影像模型正在重新看這張圖…(通常要一分鐘左右)')).toBeTruthy();
    expect((screen.getByRole('button', { name: '重新分析' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('shows why an analysis failed', async () => {
    reports.list.mockResolvedValue([reportCase({ review: { status: 'FAILED', suggestion: null, error: 'model is down' } })]);
    open();

    expect(await screen.findByText('分析失敗')).toBeTruthy();
    expect(screen.getByText('model is down')).toBeTruthy();
  });

  it('shows the proposal beside the current description, with what changed marked', async () => {
    reports.list.mockResolvedValue([reportCase({ review: doneReview() })]);
    const { container } = open();

    expect(await screen.findByText('修改建議:')).toBeTruthy();
    expect(screen.getByText('the tags missed the format')).toBeTruthy();
    expect(container.querySelector('.tag-added')?.textContent).toBe('new tag');
    expect(container.querySelector('.tag-removed')?.textContent).toBe('old tag');
    expect(container.querySelector('.line-added')?.textContent).toBe('a new example');
    expect(container.querySelectorAll('.field-changed')).toHaveLength(1); // only the meaning changed
    expect((screen.getByRole('button', { name: '套用 AI 建議' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('warns when the model thinks the picture is no meme', async () => {
    reports.list.mockResolvedValue([reportCase({ review: doneReview({ isMeme: false, reasoning: '' }) })]);
    open();

    expect(await screen.findByText('模型認為這張圖不是梗圖。')).toBeTruthy();
    expect(screen.getByText('(模型沒有說明)')).toBeTruthy();
  });

  it('applies the proposal and reads the cases again', async () => {
    reports.list.mockResolvedValue([reportCase({ review: doneReview() })]);
    open();

    fireEvent.click(await screen.findByRole('button', { name: '套用 AI 建議' }));

    await waitFor(() => expect(reports.apply).toHaveBeenCalledWith('t-1'));
    await waitFor(() => expect(reports.list).toHaveBeenCalledTimes(2));
  });

  it('dismisses the reports, and retires a meme with its reports', async () => {
    reports.list.mockResolvedValue([reportCase()]);
    open();

    fireEvent.click(await screen.findByRole('button', { name: '忽略回報' }));
    await waitFor(() => expect(reports.dismiss).toHaveBeenCalledWith('t-1'));

    fireEvent.click(await screen.findByRole('button', { name: '下架這張' }));
    await waitFor(() => expect(retire).toHaveBeenCalledWith('t-1'));
    await waitFor(() => expect(reports.dismiss).toHaveBeenCalledTimes(2));
  });

  it('shows the error of an action that failed', async () => {
    reports.list.mockResolvedValue([reportCase({ review: doneReview() })]);
    reports.apply.mockRejectedValue(new Error('refused'));
    open();

    fireEvent.click(await screen.findByRole('button', { name: '套用 AI 建議' }));

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('reads the reports again on request', async () => {
    open();
    await screen.findByText('目前沒有待處理的回報。');

    fireEvent.click(screen.getByRole('button', { name: '重新整理' }));

    await waitFor(() => expect(reports.list).toHaveBeenCalledTimes(2));
  });

  it('lists what was decided automatically, and takes a decision back', async () => {
    reports.automatic.mockResolvedValue([automaticEntry(), automaticEntry({ templateId: 't-8', action: 'DISMISSED', canUndo: false, note: null })]);
    open();

    expect(await screen.findByText('最近自動處理(2)')).toBeTruthy();
    expect(screen.getByText('已自動採用 AI 建議')).toBeTruthy();
    expect(screen.getByText('已自動忽略(模型認為沒問題)')).toBeTruthy();
    expect(screen.getByText('模型的說明:the proposal fits')).toBeTruthy();
    const reopen = screen.getByRole('button', { name: '重新打開回報' }) as HTMLButtonElement;
    expect(reopen.disabled).toBe(true); // it can no longer be taken back

    fireEvent.click(screen.getByRole('button', { name: '還原成原本的描述' }));

    await waitFor(() => expect(reports.undo).toHaveBeenCalledWith('t-9'));
    await waitFor(() => expect(reports.list).toHaveBeenCalledTimes(2));
  });

  it('shows the error of a decision that cannot be taken back', async () => {
    reports.automatic.mockResolvedValue([automaticEntry()]);
    reports.undo.mockRejectedValue(new Error('too late'));
    const { container } = open();
    await screen.findByText('最近自動處理(1)');

    fireEvent.click(screen.getByRole('button', { name: '還原成原本的描述' }));

    await waitFor(() => expect(within(container.querySelector('details')!).getByRole('alert')).toBeTruthy());
  });
});
