import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { TemplateView } from '../../api/types';

/** The template as the server has it. `refresh` reads it again and gives it back. */
export function useTemplate(id: string) {
  const [template, setTemplate] = useState<TemplateView | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  const refresh = useCallback(async () => {
    const fresh = await api.admin.getTemplate(id);
    setTemplate(fresh);
    return fresh;
  }, [id]);

  useEffect(() => {
    refresh().catch(setLoadError);
  }, [refresh]);

  return { template, loadError, refresh };
}
