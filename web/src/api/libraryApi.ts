import type { HotSearch, LibraryItem } from './types';
import { enc, request, send } from './http';

export const libraryApi = {
  /** Memes picked at random from the published library. */
  random: (limit = 12) => request<LibraryItem[]>('GET', `/api/library/random?limit=${limit}`),

  hotSearches: (limit = 8) => request<HotSearch[]>('GET', `/api/library/hot-searches?limit=${limit}`),

  /** The picture as a file, fetched through the application so a canvas may draw it and a download works. */
  async image(templateId: string): Promise<Blob> {
    const response = await send('GET', `/api/library/${enc(templateId)}/image`, {});
    return response.blob();
  },
};
