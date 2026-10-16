import type { IndexSyncResult } from './types';
import { adminCollectionApi } from './adminCollectionApi';
import { adminReportsApi } from './adminReportsApi';
import { adminTemplatesApi } from './adminTemplatesApi';
import { request } from './http';

export const adminApi = {
  ...adminTemplatesApi,

  syncIndex: () => request<IndexSyncResult>('POST', '/api/admin/index/sync'),

  reports: adminReportsApi,

  collection: adminCollectionApi,
};
