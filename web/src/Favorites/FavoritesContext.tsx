import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { LibraryItem } from '../api/types';
import { withFavorite, withoutFavorite } from '../lib/favorites';

interface FavoritesValue {
  items: LibraryItem[];
  /** True until the first answer from the server has arrived. */
  loading: boolean;
  /** Why the list could not be loaded, or null. */
  error: unknown;
  isFavorite: (templateId: string) => boolean;
  /** Adds the meme; rejects (and leaves the list as it was) when the server refuses. */
  add: (item: LibraryItem) => Promise<void>;
  remove: (templateId: string) => Promise<void>;
  /** Asks the server again, which also renews the temporary picture addresses. */
  refresh: () => Promise<void>;
}

const FavoritesContext = createContext<FavoritesValue | null>(null);

/**
 * Keeps the signed-in user's favorites in one place so every meme card can show whether it is one,
 * and starring a meme changes the card at once.
 */
export function FavoritesProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const refresh = useCallback(async () => {
    try {
      setItems(await api.favorites.list());
      setError(null);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const add = useCallback(async (item: LibraryItem) => {
    setItems((current) => withFavorite(current, item));
    try {
      await api.favorites.add(item.templateId);
    } catch (e) {
      setItems((current) => withoutFavorite(current, item.templateId));
      throw e;
    }
  }, []);

  const remove = useCallback(
    async (templateId: string) => {
      const before = items.find((i) => i.templateId === templateId);
      setItems((current) => withoutFavorite(current, templateId));
      try {
        await api.favorites.remove(templateId);
      } catch (e) {
        if (before) setItems((current) => withFavorite(current, before));
        throw e;
      }
    },
    [items],
  );

  const value = useMemo<FavoritesValue>(() => {
    const ids = new Set(items.map((i) => i.templateId));
    return { items, loading, error, isFavorite: (id) => ids.has(id), add, remove, refresh };
  }, [items, loading, error, add, remove, refresh]);

  return <FavoritesContext.Provider value={value}>{children}</FavoritesContext.Provider>;
}

export function useFavorites(): FavoritesValue {
  const value = useContext(FavoritesContext);
  if (!value) throw new Error('useFavorites must be used inside FavoritesProvider');
  return value;
}
