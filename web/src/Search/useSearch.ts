import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { RANDOM_COUNT, SEARCH_COUNT } from './searchConstants';
import type { Load } from './searchTypes';

/** The memes of the grid: random ones to begin with, then the answer to whatever was searched. */
export function useSearch() {
  /** What the grid shows: the answer to this search, or random memes when it is null. */
  const [searched, setSearched] = useState<string | null>(null);
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  // Only the newest request may show its answer; an older, slower one must not overwrite it.
  const latest = useRef(0);

  const showRandom = useCallback(async () => {
    const ticket = ++latest.current;
    setSearched(null);
    setLoad({ state: 'loading' });
    try {
      const items = await api.library.random(RANDOM_COUNT);
      if (latest.current === ticket) setLoad({ state: 'ready', items });
    } catch (error) {
      if (latest.current === ticket) setLoad({ state: 'error', error });
    }
  }, []);

  const search = useCallback(async (query: string) => {
    const q = query.trim();
    if (!q) return;
    const ticket = ++latest.current;
    setSearched(q);
    setLoad({ state: 'loading' });
    try {
      const items = await api.search(q, SEARCH_COUNT);
      if (latest.current === ticket) setLoad({ state: 'ready', items });
    } catch (error) {
      if (latest.current === ticket) setLoad({ state: 'error', error });
    }
  }, []);

  useEffect(() => {
    void showRandom();
    return () => {
      latest.current = -1;
    };
  }, [showRandom]);

  return { load, searched, search, showRandom };
}
