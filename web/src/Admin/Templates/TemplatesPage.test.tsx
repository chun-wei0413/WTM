// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { manySummaries, summary } from './templateListFixtures';
import { TemplatesPage } from './TemplatesPage';

const a = vi.hoisted(() => ({ listTemplates: vi.fn(), draftTemplate: vi.fn(), syncIndex: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: a } }));
vi.mock('../../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>;
}

function open() {
  return render(
    <MemoryRouter initialEntries={['/admin/templates']}>
      <Routes>
        <Route path="/admin/templates" element={<TemplatesPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  URL.createObjectURL = () => 'blob:preview';
  URL.revokeObjectURL = () => undefined;
  a.listTemplates.mockReset().mockResolvedValue(manySummaries(9));
  a.syncIndex.mockReset().mockResolvedValue({ indexed: 2, removed: 1, failed: 0 });
  a.draftTemplate.mockReset().mockResolvedValue({ id: 'new-1' });
});
afterEach(cleanup);

describe('TemplatesPage', () => {
  it('lists the approved templates, with how many each tab holds', async () => {
    open();
    expect(screen.getByText('載入中…')).toBeTruthy();

    expect(await screen.findByText('Meme 0')).toBeTruthy();
    expect(screen.getByRole('tab', { name: /已核准/ }).getAttribute('aria-selected')).toBe('true');
    expect(screen.getByRole('tab', { name: /全部/ }).textContent).toContain('9');
    expect(screen.getByText('3 張')).toBeTruthy();
    expect(screen.queryByText('Meme 1')).toBeNull(); // a draft
  });

  it('shows the error when the list cannot be read', async () => {
    a.listTemplates.mockRejectedValue(new Error('down'));

    open();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('changes the list with the tabs and the search box', async () => {
    open();
    await screen.findByText('Meme 0');

    fireEvent.click(screen.getByRole('tab', { name: /草稿/ }));
    expect(screen.getByText('Meme 1')).toBeTruthy();
    expect(screen.queryByText('Meme 0')).toBeNull();

    fireEvent.click(screen.getByRole('tab', { name: /全部/ }));
    fireEvent.change(screen.getByLabelText('搜尋圖庫'), { target: { value: 'meme 5' } });
    expect(screen.getByText('1 張')).toBeTruthy();
    expect(screen.getByText('Meme 5')).toBeTruthy();
  });

  it('tells the visitor when nothing matches, and when the library is empty', async () => {
    open();
    await screen.findByText('Meme 0');
    fireEvent.change(screen.getByLabelText('搜尋圖庫'), { target: { value: 'zzzz' } });
    expect(screen.getByText('沒有符合的圖。')).toBeTruthy();

    cleanup();
    a.listTemplates.mockResolvedValue([]);
    open();
    expect(await screen.findByText('這裡還沒有圖。')).toBeTruthy();
  });

  it('shows a page at a time, and the rest on request', async () => {
    a.listTemplates.mockResolvedValue(Array.from({ length: 60 }, (_, i) => summary(i)));
    open();

    expect(await screen.findByText('60 張,先顯示 48 張')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '顯示更多(還有 12 張)' }));

    expect(screen.getByText('60 張')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /顯示更多/ })).toBeNull();
  });

  it('shows where a template came from, and its meaning, and links it to its editor', async () => {
    a.listTemplates.mockResolvedValue([
      summary(1, 'APPROVED', { meaning: 'a dog in a fire', sourceType: 'WIKIMEDIA', attribution: 'CC BY-SA' }),
      summary(2, 'RETIRED'),
    ]);
    open();

    expect(await screen.findByText('a dog in a fire')).toBeTruthy();
    expect(screen.getByText('Meme 1').closest('a')?.getAttribute('href')).toBe('/admin/templates/t-1');
    fireEvent.click(screen.getByRole('tab', { name: /已下架/ }));
    expect(screen.getByText('手動建立')).toBeTruthy();
    expect(screen.getByText('Meme 2').closest('a')?.className).toContain('retired');
  });

  it('syncs the search index and says what it did', async () => {
    open();
    await screen.findByText('Meme 0');

    fireEvent.click(screen.getByRole('button', { name: '同步搜尋索引' }));

    expect(await screen.findByText(/搜尋索引已同步:建立或更新 2 個、移除 1 個/)).toBeTruthy();
  });

  it('shows the error of a sync that failed', async () => {
    a.syncIndex.mockRejectedValue(new Error('down'));
    open();
    await screen.findByText('Meme 0');

    fireEvent.click(screen.getByRole('button', { name: '同步搜尋索引' }));

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('opens and closes the form for a new template, and keeps what was typed', async () => {
    const { container } = open();
    await screen.findByText('Meme 0');
    expect(container.querySelector('.new-template')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '新增模板' }));
    fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['x'], 'my picture.png', { type: 'image/png' })] } });
    expect((container.querySelector('.new-template input') as HTMLInputElement).value).toBe('my picture');
    expect(await screen.findByAltText('預覽')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(container.querySelector('.new-template')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '新增模板' }));

    expect((container.querySelector('.new-template input') as HTMLInputElement).value).toBe('my picture');
  });

  it('creates the draft and opens its editor', async () => {
    const { container } = open();
    await screen.findByText('Meme 0');
    fireEvent.click(screen.getByRole('button', { name: '新增模板' }));
    const submit = screen.getByRole('button', { name: '建立草稿並開始編輯' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['x'], 'a.png', { type: 'image/png' })] } });
    expect(submit.disabled).toBe(false);
    fireEvent.submit(container.querySelector('.new-template')!);

    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/admin/templates/new-1'));
    expect(a.draftTemplate).toHaveBeenCalledWith('a', expect.any(File));
  });

  it('shows the error of a draft that could not be created', async () => {
    a.draftTemplate.mockRejectedValue(new Error('too big'));
    const { container } = open();
    await screen.findByText('Meme 0');
    fireEvent.click(screen.getByRole('button', { name: '新增模板' }));
    fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [new File(['x'], 'a.png', { type: 'image/png' })] } });

    fireEvent.submit(container.querySelector('.new-template')!);

    expect(await screen.findByRole('alert')).toBeTruthy();
  });
});
