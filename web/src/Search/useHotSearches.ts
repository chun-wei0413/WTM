import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { HOT_SEARCH_COUNT } from './searchConstants';

/** What people searched for lately, as shortcuts; empty until it arrives, and when it cannot be fetched. */
export function useHotSearches(): string[] {
  const [hot, setHot] = useState<string[]>([]);

  useEffect(() => {
    api.library
      .hotSearches(HOT_SEARCH_COUNT)
      .then((terms) => setHot(terms.map((t) => t.term)))
      .catch(() => undefined); // the shortcuts are a convenience; the page works without them
  }, []);

  return hot;
}
