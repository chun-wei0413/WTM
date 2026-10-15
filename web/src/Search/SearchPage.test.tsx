// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { LibraryItem, PickResult } from '../api/types';
import { SearchPage } from './SearchPage';

const { random, search, pick, hotSearches } = vi.hoisted(() => ({
  random: vi.fn(),
  search: vi.fn(),
  pick: vi.fn(),
  hotSearches: vi.fn(),
}));
vi.mock('../api/client', () => ({ api: { search, pick, library: { random, hotSearches } } }));
// The tiles are not what is tested here, and they need the favorites and sign-in providers.
vi.mock('../components/MemeTile', () => ({
  MemeTile: ({ item, reason }: { item: LibraryItem; reason?: string }) => (
    <div data-testid="tile">
      {item.name}
      {reason && <em>{reason}</em>}
    </div>
  ),
}));

function meme(name: string): LibraryItem {
  return { templateId: name, name } as LibraryItem;
}

beforeEach(() => {
  random.mockReset().mockResolvedValue([meme('隨機一張')]);
  search.mockReset().mockResolvedValue([meme('最接近的'), meme('第二近的')]);
  pick.mockReset();
  hotSearches.mockReset().mockResolvedValue([]);
});

afterEach(cleanup);

describe('SearchPage', () => {
  it('shows random memes and the example searches to begin with', async () => {
    render(<SearchPage />);

    expect(await screen.findByText('隨機一張')).toBeTruthy();
    expect(screen.getByText('隨機看看')).toBeTruthy();
    expect(screen.getByRole('button', { name: '黑人問號' })).toBeTruthy();
  });

  it('searches for what was typed and lists the closest memes', async () => {
    render(<SearchPage />);
    await screen.findByText('隨機一張');

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: '被老闆臨時加工作' } });
    fireEvent.click(screen.getByRole('button', { name: /^搜尋/ }));

    expect(await screen.findByText('最接近的')).toBeTruthy();
    expect(search).toHaveBeenCalledWith('被老闆臨時加工作', 12);
    expect(screen.getByText('最接近「被老闆臨時加工作」的')).toBeTruthy();
    expect(screen.getByText('2 張')).toBeTruthy();
  });

  it('searches at once when a shortcut is clicked, and goes back to random memes on clear', async () => {
    render(<SearchPage />);
    await screen.findByText('隨機一張');

    fireEvent.click(screen.getByRole('button', { name: '黑人問號' }));
    await screen.findByText('最接近的');
    expect(search).toHaveBeenCalledWith('黑人問號', 12);

    fireEvent.click(screen.getByRole('button', { name: /清除搜尋/ }));
    expect(await screen.findByText('隨機一張')).toBeTruthy();
  });

  it('tells the visitor when nothing was found', async () => {
    search.mockResolvedValue([]);
    render(<SearchPage />);
    await screen.findByText('隨機一張');

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'zzz' } });
    fireEvent.click(screen.getByRole('button', { name: /^搜尋/ }));

    expect(await screen.findByText('沒有找到合適的梗圖,換個說法試試。')).toBeTruthy();
  });

  it('shows the hot searches in place of the examples when there are some', async () => {
    hotSearches.mockResolvedValue([{ term: '今天很累', searches: 4 }]);
    render(<SearchPage />);

    expect(await screen.findByRole('button', { name: '今天很累' })).toBeTruthy();
    expect(screen.getByText('最近熱門')).toBeTruthy();
    expect(screen.queryByRole('button', { name: '黑人問號' })).toBeNull();
  });

  it('switches to picking, sends the situation and shows the chosen meme with its reason', async () => {
    const result: PickResult = { chosen: { ...meme('被選中的'), score: 1 } as never, reason: '因為很貼切', others: [] };
    pick.mockResolvedValue(result);
    render(<SearchPage />);
    await screen.findByText('隨機一張');

    fireEvent.click(screen.getByRole('tab', { name: '幫我挑一張' }));
    expect(screen.getByText(/把處境打在上面的框框裡/)).toBeTruthy();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '同事一直問進度' } });
    fireEvent.click(screen.getByRole('button', { name: /^幫我挑/ }));

    expect(await screen.findByText('被選中的')).toBeTruthy();
    expect(screen.getByText('因為很貼切')).toBeTruthy();
    expect(screen.getByText('幫你挑好了')).toBeTruthy();
    expect(pick).toHaveBeenCalledWith('同事一直問進度');

    fireEvent.click(screen.getByRole('button', { name: /^清除/ }));
    await waitFor(() => expect(screen.getByText(/把處境打在上面的框框裡/)).toBeTruthy());
  });

  it('sends the situation with Enter, but not with Shift+Enter', async () => {
    pick.mockResolvedValue({ chosen: null, reason: null, others: [] } satisfies PickResult);
    render(<SearchPage />);
    await screen.findByText('隨機一張');
    fireEvent.click(screen.getByRole('tab', { name: '幫我挑一張' }));
    const box = screen.getByRole('textbox');
    fireEvent.change(box, { target: { value: '今天好累' } });

    fireEvent.keyDown(box, { key: 'Enter', shiftKey: true });
    expect(pick).not.toHaveBeenCalled();

    fireEvent.keyDown(box, { key: 'Enter' });
    await waitFor(() => expect(pick).toHaveBeenCalledWith('今天好累'));
  });
});
