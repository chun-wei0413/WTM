import type { ReportReason } from './types';
import { request } from './http';

export const reportApi = {
  /** Tells the administrators a meme's description or tags do not fit it. */
  submit: (templateId: string, reason: ReportReason, comment: string) =>
    request<void>('POST', '/api/reports', { json: { templateId, reason, comment } }),
};
