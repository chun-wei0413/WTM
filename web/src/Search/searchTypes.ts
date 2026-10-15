import type { LibraryItem, PickResult } from '../api/types';

/** What the grid shows: memes still loading, a failed request, or the answer. */
export type Load =
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; items: LibraryItem[] };

/** "search" lists the closest memes; "pick" lets the model choose one and say why. */
export type Mode = 'search' | 'pick';

export type Picked =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; result: PickResult };
