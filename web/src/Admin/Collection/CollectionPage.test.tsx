// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CollectionPage } from './CollectionPage';

const c = vi.hoisted(() => ({ stats: vi.fn(), addFiles: vi.fn(), addUrl: vi.fn(), syncIndex: vi.fn() }));
vi.mock('../../api/client', () => ({ api: { admin: { collection: c, syncIndex: c.syncIndex } } }));
vi.mock('../../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));

const png = (name: string) => new File(['x'], name, { type: 'image/png' });

function open() {
  return render(
    <MemoryRouter>
      <CollectionPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  Object.values(c).forEach((fn) => fn.mockReset());
  c.stats.mockResolvedValue({ total: 10, approved: 7, retired: 1, waitingForTags: 0, beingTagged: 0, tagFailures: 0 });
  c.syncIndex.mockResolvedValue({ indexed: 3, removed: 1, failed: 0 });
  c.addFiles.mockImplementation(async (files: File[]) =>
    files.map((f) => ({ fileName: f.name, status: 'IMPORTED', templateId: 't', reason: null })),
  );
  c.addUrl.mockResolvedValue({ status: 'IMPORTED', templateId: 't', reason: null, fileName: 'a' });
});
afterEach(cleanup);

describe('CollectionPage', () => {
  it('shows the numbers of the library', async () => {
    open();

    expect(await screen.findByText('圖庫總數')).toBeTruthy();
    expect(screen.getByText('可被搜尋(已核准)').closest('.stat')?.textContent).toContain('7');
    expect(screen.getByRole('link', { name: '管理與核准' }).getAttribute('href')).toBe('/admin/templates');
  });

  it('marks failed tags as a warning, and says how many pictures wait for the model', async () => {
    c.stats.mockResolvedValue({ total: 10, approved: 5, retired: 0, waitingForTags: 3, beingTagged: 1, tagFailures: 2 });

    const { container } = open();

    expect(await screen.findByText('還有 4 張正在等視覺模型標記,這頁會自動更新。')).toBeTruthy();
    expect(container.querySelectorAll('.stat-warn')).toHaveLength(1);
  });

  it('shows the error when the numbers cannot be read', async () => {
    c.stats.mockRejectedValue(new Error('down'));

    open();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('syncs the search index and says what it did, with failures', async () => {
    c.syncIndex.mockResolvedValue({ indexed: 3, removed: 1, failed: 2 });
    open();
    await screen.findByText('圖庫總數');

    fireEvent.click(screen.getByRole('button', { name: '同步搜尋索引' }));

    expect(await screen.findByText(/搜尋索引已同步:建立或更新 3 個、移除 1 個,有 2 個失敗/)).toBeTruthy();
    await waitFor(() => expect(c.stats).toHaveBeenCalledTimes(2)); // the numbers are read again
  });

  it('uploads the chosen pictures and says how many got in', async () => {
    const { container } = open();
    await screen.findByText('圖庫總數');

    fireEvent.change(container.querySelector('input[type=file]')!, { target: { files: [png('a.png'), png('b.png')] } });

    expect(await screen.findByText(/2/)).toBeTruthy();
    await waitFor(() => expect(c.addFiles).toHaveBeenCalledTimes(1));
    expect(container.querySelector('.notice-success')).not.toBeNull();
  });

  it('lists the pictures the library turned away, ten at most', async () => {
    c.addFiles.mockImplementation(async (files: File[]) =>
      files.map((f) => ({ fileName: f.name, status: 'REJECTED', templateId: null, reason: 'too small' })),
    );
    const { container } = open();
    await screen.findByText('圖庫總數');

    fireEvent.change(container.querySelector('input[type=file]')!, {
      target: { files: Array.from({ length: 12 }, (_, i) => png(`p${i}.png`)) },
    });

    expect(await screen.findByText('…還有 2 個')).toBeTruthy();
    expect(container.querySelectorAll('.reject-list li')).toHaveLength(11);
  });

  it('adds a picture from a web address, and empties the form', async () => {
    const { container } = open();
    const url = (await screen.findByLabelText(/^圖片網址/)) as HTMLInputElement;
    const add = screen.getByRole('button', { name: '加入圖庫' }) as HTMLButtonElement;
    expect(add.disabled).toBe(true);

    fireEvent.change(url, { target: { value: 'https://example.com/a.jpg' } });
    expect(add.disabled).toBe(false);
    fireEvent.submit(container.querySelector('.url-form')!);

    expect(await screen.findByText('已加入圖庫,稍後會自動標記。')).toBeTruthy();
    expect(c.addUrl).toHaveBeenCalledWith('https://example.com/a.jpg', undefined);
    expect(url.value).toBe('');
  });

  it('says when the picture is already in the library, and when the address cannot be used', async () => {
    const { container } = open();
    const url = (await screen.findByLabelText(/^圖片網址/)) as HTMLInputElement;
    fireEvent.change(url, { target: { value: 'https://example.com/a.jpg' } });

    c.addUrl.mockResolvedValueOnce({ status: 'DUPLICATE', templateId: null, reason: null, fileName: 'a' });
    fireEvent.submit(container.querySelector('.url-form')!);
    expect(await screen.findByText('這張圖已經在圖庫裡了。')).toBeTruthy();

    c.addUrl.mockResolvedValueOnce({ status: 'REJECTED', templateId: null, reason: 'not an image', fileName: 'a' });
    fireEvent.submit(container.querySelector('.url-form')!);
    expect(await screen.findByText('這個網址的圖片不能用:not an image')).toBeTruthy();
  });
});
