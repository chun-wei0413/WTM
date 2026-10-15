import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';
import type { TemplateSummary } from '../../api/types';
import { countByStatus, filterTemplates, type StatusFilter } from '../../lib/templateList';

export type Load = { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; templates: TemplateSummary[] };

/** How many entries are drawn at first, and added each time "show more" is pressed. */
export const PAGE = 48;

/** The library's templates, filtered by status and a search text, and how many of the matches are shown. */
export function useTemplateList() {
  const [status, setStatus] = useState<StatusFilter>('APPROVED');
  const [query, setQuery] = useState('');
  const [shown, setShown] = useState(PAGE);
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  // The whole list is fetched once; choosing a tab or typing a search then answers at once.
  useEffect(() => {
    let cancelled = false;
    api.admin
      .listTemplates()
      .then((templates) => !cancelled && setLoad({ state: 'ready', templates }))
      .catch((error: unknown) => !cancelled && setLoad({ state: 'error', error }));
    return () => {
      cancelled = true;
    };
  }, []);

  const all = load.state === 'ready' ? load.templates : [];
  const counts = useMemo(() => countByStatus(all), [all]);
  const matching = useMemo(() => filterTemplates(all, status, query), [all, status, query]);

  function choose(next: StatusFilter) {
    setStatus(next);
    setShown(PAGE);
  }

  function search(text: string) {
    setQuery(text);
    setShown(PAGE);
  }

  function showMore() {
    setShown((n) => n + PAGE);
  }

  return { load, status, query, shown, counts, matching, choose, search, showMore };
}
