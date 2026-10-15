import { useState } from 'react';
import { api } from '../../api/client';
import type { ReportCase } from '../../api/types';
import { reviewState } from './caseReview';

/**
 * What an administrator can do with one reported meme: apply the model's proposal, dismiss the reports, ask the model
 * again, or retire the meme. `busy` names the action in progress; `onChanged` runs after one has gone through.
 */
export function useCaseActions(item: ReportCase, onChanged: () => void) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const { suggestion } = reviewState(item);

  async function run(label: string, action: () => Promise<unknown>) {
    setBusy(label);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  function apply() {
    if (suggestion && !suggestion.isMeme && !window.confirm('模型認為這不是梗圖。確定還是要套用它的描述嗎?')) return;
    void run('apply', () => api.admin.reports.apply(item.templateId));
  }

  function dismiss() {
    void run('dismiss', () => api.admin.reports.dismiss(item.templateId));
  }

  function reanalyze() {
    void run('reanalyze', () => api.admin.reports.reanalyze(item.templateId));
  }

  function retire() {
    if (!window.confirm('下架後這張梗圖不會再被搜尋到,所有回報也會一併結案。確定嗎?')) return;
    void run('retire', async () => {
      await api.admin.retire(item.templateId);
      await api.admin.reports.dismiss(item.templateId);
    });
  }

  return { busy, error, apply, dismiss, reanalyze, retire };
}
