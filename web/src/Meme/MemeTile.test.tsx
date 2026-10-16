// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installDialog } from '../testSupport/dialog';
import { meme } from './memeFixtures';
import { MemeTile } from './MemeTile';

const h = vi.hoisted(() => ({ image: vi.fn(), submit: vi.fn(), saveBlob: vi.fn(), add: vi.fn(), remove: vi.fn(), favorites: new Set<string>() }));
vi.mock('../api/client', () => ({ api: { library: { image: h.image }, reports: { submit: h.submit } } }));
vi.mock('../lib/download', () => ({ saveBlob: h.saveBlob, fileNameFor: (base: string) => `${base}.png` }));
vi.mock('../favorites/FavoritesContext', () => ({
  useFavorites: () => ({ isFavorite: (id: string) => h.favorites.has(id), add: h.add, remove: h.remove }),
}));
vi.mock('../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));

beforeEach(() => {
  installDialog();
  h.favorites = new Set();
  h.image.mockReset().mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
  h.submit.mockReset().mockResolvedValue(undefined);
  h.saveBlob.mockReset();
  h.add.mockReset().mockResolvedValue(undefined);
  h.remove.mockReset().mockResolvedValue(undefined);
});
afterEach(cleanup);

describe('MemeTile as a card', () => {
  it('shows the picture, and keeps the meaning and tags behind the details button', () => {
    render(<MemeTile item={meme()} />);

    expect(screen.getByAltText('prefers one thing over another')).toBeTruthy();
    expect(screen.queryByText('prefers one thing over another', { selector: 'p' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '詳情' }));

    expect(screen.getByText('prefers one thing over another', { selector: 'p' })).toBeTruthy();
    expect(screen.getAllByRole('listitem')).toHaveLength(8); // at most eight tags
    expect(screen.getByRole('button', { name: '收起' }).getAttribute('aria-expanded')).toBe('true');
  });

  it('shows the buttons that were put beside download and favorite', () => {
    render(
      <MemeTile item={meme()}>
        <a href="/create">加文字</a>
      </MemeTile>,
    );

    expect(screen.getByRole('link', { name: '加文字' })).toBeTruthy();
  });

  it('downloads the picture', async () => {
    render(<MemeTile item={meme()} />);

    fireEvent.click(screen.getByRole('button', { name: '下載' }));

    await waitFor(() => expect(h.saveBlob).toHaveBeenCalledWith(expect.any(Blob), 'meme-abcdef12.png'));
  });

  it('stars a meme and unstars a favorite', async () => {
    const { unmount } = render(<MemeTile item={meme()} />);
    fireEvent.click(screen.getByRole('button', { name: '☆ 收藏' }));
    await waitFor(() => expect(h.add).toHaveBeenCalledTimes(1));
    unmount();

    h.favorites = new Set(['abcdef123456']);
    render(<MemeTile item={meme()} />);
    const star = screen.getByRole('button', { name: '★ 已收藏' });
    expect(star.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(star);
    await waitFor(() => expect(h.remove).toHaveBeenCalledWith('abcdef123456'));
  });

  it('shows the error of a download that failed', async () => {
    h.image.mockRejectedValue(new Error('gone'));
    render(<MemeTile item={meme()} />);

    fireEvent.click(screen.getByRole('button', { name: '下載' }));

    expect(await screen.findByRole('alert')).toBeTruthy();
  });
});

describe('MemeTile in the playful layout', () => {
  it('numbers the meme, shows its meaning and a few tags', () => {
    render(<MemeTile item={meme()} index={3} />);

    expect(screen.getByText('03')).toBeTruthy();
    expect(screen.getByText('prefers one thing over another')).toBeTruthy();
    expect(screen.getAllByRole('listitem')).toHaveLength(4);
    expect(screen.getByRole('button', { name: '來源・回報' })).toBeTruthy();
  });

  it('shows more tags for the lead, words instead of the number for a pick, and the reason', () => {
    render(<MemeTile item={meme()} index={1} lead badge="最接近!" reason="it fits" />);

    expect(screen.getByText('最接近!')).toBeTruthy();
    expect(screen.getByText('it fits')).toBeTruthy();
    expect(screen.getByText('AI 寫的,不一定準')).toBeTruthy();
    expect(screen.getAllByRole('listitem')).toHaveLength(8);
  });
});

describe('MemeTile opened large', () => {
  it('opens when the picture is clicked, with the usage and the text in the picture, and closes again', async () => {
    const item = meme({ usageExamples: ['when you say no'] } as never);
    const { container } = render(<MemeTile item={item} />);
    expect(container.querySelector('dialog')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: '放大看「Drake」' }));

    const dialog = container.querySelector('dialog')!;
    expect(within(dialog).getByRole('heading', { name: 'Drake' })).toBeTruthy();
    expect(within(dialog).getByText('when you say no')).toBeTruthy();
    expect(within(dialog).getByText('NO YES')).toBeTruthy();
    expect(within(dialog).getAllByRole('listitem').length).toBeGreaterThan(10); // all the tags and the usage

    fireEvent.click(within(dialog).getByRole('button', { name: '關閉' }));
    expect(container.querySelector('dialog')).toBeNull();
  });

  it('opens from the meaning in the playful layout, and closes by a click on the backdrop', () => {
    const { container } = render(<MemeTile item={meme()} index={1} />);

    fireEvent.click(screen.getByTitle('看完整說明'));
    const dialog = container.querySelector('dialog')!;
    expect(dialog).not.toBeNull();

    fireEvent.click(dialog);
    expect(container.querySelector('dialog')).toBeNull();
  });

  it('leaves out the usage and the text in the picture when a favorite does not have them', () => {
    const { container } = render(<MemeTile item={meme({ imageText: '  ' })} />);

    fireEvent.click(screen.getByRole('button', { name: '放大看「Drake」' }));

    const dialog = container.querySelector('dialog')!;
    expect(within(dialog).queryByText('什麼時候用')).toBeNull();
    expect(within(dialog).queryByText('圖上的字')).toBeNull();
  });
});

describe('MemeTile details', () => {
  it('says where the picture comes from, linking only a web address', () => {
    const { container, rerender } = render(<MemeTile item={meme()} />);
    fireEvent.click(screen.getByRole('button', { name: '詳情' }));
    expect(screen.getByRole('link', { name: '維基共享資源' }).getAttribute('href')).toBe('https://example.com/source');
    expect(container.textContent).toContain('(CC BY)');

    rerender(<MemeTile item={meme({ sourceUrl: 'javascript:alert(1)' })} />);
    expect(screen.queryByRole('link', { name: '維基共享資源' })).toBeNull();
  });

  it('shows what the source wrote about the meme, with its credit', () => {
    render(
      <MemeTile
        item={meme({ reference: { text: 'about the meme', sourceName: 'Wikipedia', url: 'https://wiki.example', license: 'CC BY-SA' } })}
      />,
    );
    fireEvent.click(screen.getByRole('button', { name: '詳情' }));

    expect(screen.getByText('about the meme')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Wikipedia' }).getAttribute('href')).toBe('https://wiki.example');
    expect(screen.getByText(/CC BY-SA/)).toBeTruthy();
  });

  it('reports a meme: choose a reason, send it, and the button then says so', async () => {
    render(<MemeTile item={meme()} />);
    fireEvent.click(screen.getByRole('button', { name: '詳情' }));

    fireEvent.click(screen.getByRole('button', { name: '回報問題' }));
    fireEvent.change(screen.getByLabelText('哪裡不對?'), { target: { value: 'NOT_A_MEME' } });
    fireEvent.click(screen.getByRole('button', { name: '送出回報' }));

    expect(await screen.findByText(/已回報,謝謝你/)).toBeTruthy();
    expect(h.submit).toHaveBeenCalledWith('abcdef123456', 'NOT_A_MEME', '');
    expect((screen.getByRole('button', { name: '已回報' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('closes the report form on cancel, and blocks a comment that is too long', () => {
    render(<MemeTile item={meme()} />);
    fireEvent.click(screen.getByRole('button', { name: '詳情' }));
    fireEvent.click(screen.getByRole('button', { name: '回報問題' }));

    fireEvent.change(screen.getByLabelText(/補充說明/), { target: { value: 'x'.repeat(301) } });
    expect((screen.getByRole('button', { name: '送出回報' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(screen.queryByLabelText('哪裡不對?')).toBeNull();
  });

  it('shows the same report state in the large view as on the card', async () => {
    const { container } = render(<MemeTile item={meme()} />);
    fireEvent.click(screen.getByRole('button', { name: '詳情' }));
    fireEvent.click(screen.getByRole('button', { name: '回報問題' }));
    fireEvent.click(screen.getByRole('button', { name: '送出回報' }));
    await screen.findByText(/已回報,謝謝你/);

    fireEvent.click(screen.getByRole('button', { name: '放大看「Drake」' }));

    expect(within(container.querySelector('dialog')!).getByText(/已回報,謝謝你/)).toBeTruthy();
  });
});
