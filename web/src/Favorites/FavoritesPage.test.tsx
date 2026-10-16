// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FavoritesPage } from './FavoritesPage';

const h = vi.hoisted(() => ({ value: { items: [] as unknown[], loading: false, error: null as unknown, refresh: vi.fn() } }));
vi.mock('./FavoritesContext', () => ({ useFavorites: () => h.value }));
vi.mock('../Meme/MemeTile', () => ({
  MemeTile: ({ item, children }: { item: { name: string }; children: ReactNode }) => (
    <div data-testid="tile">
      {item.name}
      {children}
    </div>
  ),
}));
vi.mock('../components/ErrorNotice', () => ({ ErrorNotice: () => <p role="alert">error</p> }));

function open() {
  return render(
    <MemoryRouter>
      <FavoritesPage />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  h.value = { items: [], loading: false, error: null, refresh: vi.fn() };
});
afterEach(cleanup);

describe('FavoritesPage', () => {
  it('asks for the favorites again each time it is opened, because the picture addresses expire', () => {
    open();

    expect(h.value.refresh).toHaveBeenCalledTimes(1);
  });

  it('says it is loading', () => {
    h.value.loading = true;

    open();

    expect(screen.getByText('載入中…')).toBeTruthy();
    expect(screen.queryByText('還沒有收藏任何梗圖。')).toBeNull();
  });

  it('shows the error', () => {
    h.value.error = new Error('down');

    open();

    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.queryByText('還沒有收藏任何梗圖。')).toBeNull();
  });

  it('points a visitor with no favorites to the search', () => {
    open();

    expect(screen.getByText('還沒有收藏任何梗圖。')).toBeTruthy();
    expect(screen.getByRole('link', { name: '去找梗圖' }).getAttribute('href')).toBe('/');
  });

  it('shows each favorite with a button to put text on it', () => {
    h.value.items = [
      { templateId: 'a1', name: 'First' },
      { templateId: 'b2', name: 'Second' },
    ];

    open();

    expect(screen.getAllByTestId('tile')).toHaveLength(2);
    expect(screen.getAllByRole('link', { name: '加文字' }).map((l) => l.getAttribute('href'))).toEqual([
      '/create?from=a1',
      '/create?from=b2',
    ]);
    expect(screen.queryByText('還沒有收藏任何梗圖。')).toBeNull();
  });
});
