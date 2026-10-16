import type { LibraryItem } from '../api/types';

export function meme(patch: Partial<LibraryItem> = {}): LibraryItem {
  return {
    templateId: 'abcdef123456',
    name: 'Drake',
    imageUrl: 'http://img/a.png',
    imageWidth: 400,
    imageHeight: 300,
    meaning: 'prefers one thing over another',
    tags: ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'],
    imageText: 'NO YES',
    sourceType: 'WIKIMEDIA',
    sourceUrl: 'https://example.com/source',
    attribution: 'CC BY',
    reference: null,
    ...patch,
  } as LibraryItem;
}
