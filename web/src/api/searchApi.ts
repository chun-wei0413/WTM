import type { PickResult, SearchResult } from './types';
import { enc, request } from './http';

export const searchApi = {
  search: (query: string, limit = 10) =>
    request<SearchResult[]>('GET', `/api/templates/search?q=${enc(query)}&limit=${limit}`),

  /** Describe a situation; the closest memes come back with one chosen and a reason. Takes a few seconds. */
  pick: (situation: string) => request<PickResult>('POST', '/api/templates/pick', { json: { situation } }),
};
