import { useState } from 'react';
import { api } from '../api/client';
import type { LibraryItem } from '../api/types';
import { useFavorites } from '../Favorites/FavoritesContext';
import { fileNameFor, saveBlob } from '../lib/download';

/** The two things people do with a meme: download the picture, and star it as a favorite. */
export function useTileActions(item: LibraryItem) {
  const { isFavorite, add, remove } = useFavorites();
  const favorite = isFavorite(item.templateId);
  const [downloading, setDownloading] = useState(false);
  const [starring, setStarring] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function download() {
    setDownloading(true);
    setError(null);
    try {
      const blob = await api.library.image(item.templateId);
      saveBlob(blob, fileNameFor(`meme-${item.templateId.slice(0, 8)}`, blob));
    } catch (e) {
      setError(e);
    } finally {
      setDownloading(false);
    }
  }

  async function toggleFavorite() {
    setStarring(true);
    setError(null);
    try {
      await (favorite ? remove(item.templateId) : add(item));
    } catch (e) {
      setError(e);
    } finally {
      setStarring(false);
    }
  }

  return { favorite, downloading, starring, error, download, toggleFavorite };
}
