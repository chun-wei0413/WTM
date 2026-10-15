import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { LibraryStats } from '../../api/types';

/** How often to look at the numbers again while pictures are still waiting for their tags. */
const POLL_MS = 3_000;

/** The size of the library and how many pictures wait for tags; `refresh` reads the numbers again. */
export function useLibraryStats() {
  const [stats, setStats] = useState<LibraryStats | null>(null);
  const [error, setError] = useState<unknown>(null);

  const refresh = useCallback(async () => {
    try {
      setStats(await api.admin.collection.stats());
      setError(null);
    } catch (e) {
      setError(e);
    }
  }, []);

  // While pictures are still waiting for their tags the numbers keep changing, so keep looking.
  const pending = stats ? stats.waitingForTags + stats.beingTagged : 0;
  useEffect(() => {
    void refresh();
  }, [refresh]);
  useEffect(() => {
    if (pending === 0) return;
    const timer = setInterval(() => void refresh(), POLL_MS);
    return () => clearInterval(timer);
  }, [pending, refresh]);

  return { stats, error, pending, refresh };
}
