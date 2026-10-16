import type { IngestResult, LibraryStats } from './types';
import { request } from './http';

/** What an administrator does to fill the library: look at its numbers, add pictures from files or an address. */
export const adminCollectionApi = {
  stats: () => request<LibraryStats>('GET', '/api/admin/collection/status'),

  addFiles(files: File[]) {
    const form = new FormData();
    for (const file of files) form.append('files', file);
    return request<IngestResult[]>('POST', '/api/admin/collection/files', { form });
  },

  addUrl: (url: string, title?: string) =>
    request<IngestResult>('POST', '/api/admin/collection/url', { json: { url, title: title || null } }),
};
