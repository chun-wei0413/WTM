import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { AutomaticEntry, ReportCase } from '../../api/types';
import { REPORT_POLL_MS, isReviewing } from '../../lib/reports';

export type Load =
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; cases: ReportCase[]; automatic: AutomaticEntry[] };

/** The reported memes and what was decided automatically; `refresh` reads them again. */
export function useReports() {
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  const refresh = useCallback(async () => {
    try {
      const [cases, automatic] = await Promise.all([api.admin.reports.list(), api.admin.reports.automatic()]);
      setLoad({ state: 'ready', cases, automatic });
    } catch (error) {
      setLoad((previous) => (previous.state === 'ready' ? previous : { state: 'error', error }));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // While the vision model is still working on a case, look again every few seconds.
  const reviewing = load.state === 'ready' && isReviewing(load.cases);
  useEffect(() => {
    if (!reviewing) return;
    const timer = setInterval(() => void refresh(), REPORT_POLL_MS);
    return () => clearInterval(timer);
  }, [reviewing, refresh]);

  return { load, refresh };
}
