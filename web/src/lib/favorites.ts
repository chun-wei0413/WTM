import type { LibraryItem } from '../api/types';

/** The list with `item` added at the front; unchanged when it is already there. */
export function withFavorite(items: readonly LibraryItem[], item: LibraryItem): LibraryItem[] {
  return items.some((i) => i.templateId === item.templateId) ? [...items] : [item, ...items];
}

export function withoutFavorite(items: readonly LibraryItem[], templateId: string): LibraryItem[] {
  return items.filter((i) => i.templateId !== templateId);
}
