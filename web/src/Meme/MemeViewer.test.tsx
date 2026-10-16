// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { installDialog } from '../testSupport/dialog';
import { meme } from './memeFixtures';
import { MemeTile } from './MemeTile';

vi.mock('../api/client', () => ({ api: {} }));
vi.mock('../lib/download', () => ({ saveBlob: vi.fn(), fileNameFor: (base: string) => base }));
vi.mock('../Favorites/FavoritesContext', () => ({
  useFavorites: () => ({ isFavorite: () => false, add: vi.fn(), remove: vi.fn() }),
}));
vi.mock('../components/ErrorNotice', () => ({ ErrorNotice: () => null }));

const pause = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

beforeEach(() => installDialog({ announceLater: true }));
afterEach(cleanup);

describe('MemeViewer while developing, when React checks its cleanup', () => {
  it('stays open after the picture is clicked', async () => {
    const { container } = render(
      <StrictMode>
        <MemeTile item={meme()} />
      </StrictMode>,
    );

    fireEvent.click(screen.getByRole('button', { name: '放大看「Drake」' }));
    await pause(50); // long enough for the browser to announce the close React caused

    const dialog = container.querySelector('dialog');
    expect(dialog).not.toBeNull();
    expect(dialog?.hasAttribute('open')).toBe(true);
  });

  it('still closes when the visitor closes it, and opens again afterwards', async () => {
    const { container } = render(
      <StrictMode>
        <MemeTile item={meme()} />
      </StrictMode>,
    );
    const open = () => fireEvent.click(screen.getByRole('button', { name: '放大看「Drake」' }));

    open();
    await pause(50);
    fireEvent.click(screen.getByRole('button', { name: '關閉' }));
    await waitFor(() => expect(container.querySelector('dialog')).toBeNull());

    open();
    await pause(50);
    expect(container.querySelector('dialog')?.hasAttribute('open')).toBe(true);
  });

  it('closes on a click on the backdrop', async () => {
    const { container } = render(
      <StrictMode>
        <MemeTile item={meme()} />
      </StrictMode>,
    );
    fireEvent.click(screen.getByRole('button', { name: '放大看「Drake」' }));
    await pause(50);

    fireEvent.click(container.querySelector('dialog')!);

    await waitFor(() => expect(container.querySelector('dialog')).toBeNull());
  });
});
