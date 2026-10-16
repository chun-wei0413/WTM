import type { AutomaticEntry, ReportCase } from './types';
import { enc, request } from './http';

/** What an administrator does with the reports users sent about memes. */
export const adminReportsApi = {
  list: () => request<ReportCase[]>('GET', '/api/admin/reports'),

  /** What the rules closed or adopted on their own in the last week. */
  automatic: () => request<AutomaticEntry[]>('GET', '/api/admin/reports/automatic'),

  /** Takes back an automatic decision: the earlier description, or the closed reports. */
  undo: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/undo`),

  /** Adopts the model's proposal as the meme's description and closes its reports. */
  apply: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/apply`),

  dismiss: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/dismiss`),

  /** Has the vision model look at the meme again. */
  reanalyze: (templateId: string) => request<void>('POST', `/api/admin/reports/${enc(templateId)}/reanalyze`),
};
