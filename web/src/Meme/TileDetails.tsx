import type { ReactNode } from 'react';
import type { LibraryItem } from '../api/types';

interface Props {
  item: LibraryItem;
  /** The playful layout already shows the meaning and the tags under the picture. */
  pop: boolean;
  /** Where it comes from and the report button. */
  children: ReactNode;
}

/** What opens under a card's "詳情": the meaning and tags the plain layout cuts short, then the details. */
export function TileDetails({ item, pop, children }: Props) {
  return (
    <div className="tile-details">
      {!pop && item.meaning && <p className="tile-meaning">{item.meaning}</p>}
      {!pop && item.tags.length > 0 && (
        <ul className="tag-list" aria-label="標籤">
          {item.tags.slice(0, 8).map((tag) => (
            <li key={tag}>{tag}</li>
          ))}
        </ul>
      )}
      {children}
    </div>
  );
}
