import type { LibraryItem } from './types';
import { enc, request } from './http';

export const favoritesApi = {
  list: () => request<LibraryItem[]>('GET', '/api/favorites'),

  add: (templateId: string) => request<void>('PUT', `/api/favorites/${enc(templateId)}`),

  remove: (templateId: string) => request<void>('DELETE', `/api/favorites/${enc(templateId)}`),
};
