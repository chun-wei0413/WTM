// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installPicturePointer } from '../testSupport/pointer';
import { CreatePage } from './CreatePage';

const h = vi.hoisted(() => ({
  image: vi.fn(),
  favorites: { items: [] as unknown[], loading: false, error: null as unknown },
  renderMeme: vi.fn(),
  saveBlob: vi.fn(),
}));
vi.mock('../api/client', () => ({ api: { library: { image: h.image } } }));
vi.mock('../Favorites/FavoritesContext', () => ({ useFavorites: () => h.favorites }));
vi.mock('../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));
vi.mock('../lib/renderMeme', () => ({
  canvasMeasure: () => (text: string, size: number) => text.length * size * 0.6,
  renderMeme: h.renderMeme,
}));
vi.mock('../lib/download', () => ({ saveBlob: h.saveBlob }));

function open(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/create" element={<CreatePage />} />
        <Route path="/" element={<p>home</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  installPicturePointer();
  vi.stubGlobal(
    'Image',
    class {
      src = '';
      naturalWidth = 400;
      naturalHeight = 300;
      async decode() {}
    },
  );
  URL.createObjectURL = () => 'blob:picture';
  URL.revokeObjectURL = () => undefined;
  h.favorites = { items: [], loading: false, error: null };
  h.image.mockReset().mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
  h.renderMeme.mockReset().mockResolvedValue(new Blob(['png']));
  h.saveBlob.mockReset();
});
afterEach(cleanup);

describe('CreatePage without a picture chosen', () => {
  it('tells a visitor with no favorites to go and find one', () => {
    open('/create');

    expect(screen.getByText('你還沒有收藏任何梗圖,先去找一張喜歡的收藏起來。')).toBeTruthy();
    expect(screen.getByRole('link', { name: '去找梗圖' }).getAttribute('href')).toBe('/');
  });

  it('says it is loading, and shows an error', () => {
    h.favorites = { items: [], loading: true, error: null };
    const { unmount } = open('/create');
    expect(screen.getByText('載入中…')).toBeTruthy();
    unmount();

    h.favorites = { items: [], loading: false, error: new Error('x') };
    open('/create');
    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('lists the favorites, each linking to making a meme from it', () => {
    h.favorites = {
      items: [
        { templateId: 'a1', name: 'A', meaning: 'the meaning', imageUrl: 'http://x/a.png' },
        { templateId: 'b2', name: 'B', meaning: null, imageUrl: 'http://x/b.png' },
      ],
      loading: false,
      error: null,
    };

    open('/create');

    expect(screen.getByRole('heading', { name: '選一張收藏的梗圖' })).toBeTruthy();
    expect(screen.getByAltText('the meaning').closest('a')?.getAttribute('href')).toBe('/create?from=a1');
    expect(screen.getByAltText('B').closest('a')?.getAttribute('href')).toBe('/create?from=b2');
  });
});

describe('CreatePage with a picture chosen', () => {
  it('loads the picture, then lets the visitor add text boxes', async () => {
    open('/create?from=abcdef123456');
    expect(screen.getByText('載入圖片中…')).toBeTruthy();
    expect(await screen.findByRole('button', { name: '新增文字框' })).toBeTruthy();
    expect(h.image).toHaveBeenCalledWith('abcdef123456');
    expect(screen.getByText(/還沒有文字框/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: '新增文字框' }));

    const field = screen.getByLabelText(/文字 1/, { selector: 'textarea' }) as HTMLTextAreaElement;
    expect(document.activeElement).toBe(field);
    fireEvent.change(field, { target: { value: '你好' } });
    expect((screen.getByLabelText(/文字 1/, { selector: 'textarea' }) as HTMLTextAreaElement).value).toBe('你好');
    expect(screen.queryByText(/還沒有文字框/)).toBeNull();
  });

  it('changes the look of a box and deletes it', async () => {
    open('/create?from=abcdef123456');
    fireEvent.click(await screen.findByRole('button', { name: '新增文字框' }));

    fireEvent.change(screen.getByLabelText('文字 1 的樣式'), { target: { value: 'yellow' } });
    expect((screen.getByLabelText('文字 1 的樣式') as HTMLSelectElement).value).toBe('yellow');

    fireEvent.click(screen.getByRole('button', { name: '刪除' }));
    expect(screen.queryByLabelText(/文字 1/, { selector: 'textarea' })).toBeNull();
  });

  it('draws a box where the visitor drags on the picture', async () => {
    const { container } = open('/create?from=abcdef123456');
    await screen.findByRole('button', { name: '新增文字框' });
    const svg = container.querySelector('svg')!;

    fireEvent.pointerDown(svg, { clientX: 20, clientY: 20, pointerId: 1 });
    fireEvent.pointerMove(svg, { clientX: 200, clientY: 100, pointerId: 1 });
    fireEvent.pointerUp(svg, { clientX: 200, clientY: 100, pointerId: 1 });

    expect((screen.getByLabelText(/文字 1/, { selector: 'textarea' }) as HTMLTextAreaElement).value).toBe('輸入文字');
  });

  it('downloads the meme under a name made from the template', async () => {
    open('/create?from=abcdef123456');
    fireEvent.click(await screen.findByRole('button', { name: '新增文字框' }));

    fireEvent.click(screen.getByRole('button', { name: '下載' }));

    await waitFor(() => expect(h.saveBlob).toHaveBeenCalledWith(expect.any(Blob), 'my-meme-abcdef12.png'));
  });

  it('shows the error of a download that fails', async () => {
    h.renderMeme.mockRejectedValue(new Error('no canvas'));
    open('/create?from=abcdef123456');
    fireEvent.click(await screen.findByRole('button', { name: '下載' }));

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(h.saveBlob).not.toHaveBeenCalled();
  });

  it('warns that a GIF becomes a still picture', async () => {
    h.image.mockResolvedValue(new Blob(['x'], { type: 'image/gif' }));

    open('/create?from=abcdef123456');

    expect(await screen.findByText('這是 GIF 動圖,加上文字後會存成靜態圖片。')).toBeTruthy();
  });

  it('shows the error when the picture cannot be loaded', async () => {
    h.image.mockRejectedValue(new Error('gone'));

    open('/create?from=abcdef123456');

    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.queryByRole('button', { name: '新增文字框' })).toBeNull();
  });

  it('links back to choosing another picture', async () => {
    open('/create?from=abcdef123456');

    await waitFor(() => expect(screen.getByRole('link', { name: '換一張' }).getAttribute('href')).toBe('/create'));
  });
});
