import { useState } from 'react';
import { api } from '../../api/client';

/** Takes back something the rules decided on their own; `busy` is the template being taken back. */
export function useAutomaticUndo(onChanged: () => void) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);

  async function undo(templateId: string) {
    setBusy(templateId);
    setError(null);
    try {
      await api.admin.reports.undo(templateId);
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  return { busy, error, undo };
}
