import { Link } from 'react-router-dom';
import type { LibraryItem } from '../api/types';
import { MemeTile } from '../Meme/MemeTile';

/** The favorite memes as cards, each with a button to put text on it. */
export function FavoritesGrid({ items }: { items: LibraryItem[] }) {
  return (
    <div className="masonry">
      {items.map((item) => (
        <MemeTile key={item.templateId} item={item}>
          <Link to={`/create?from=${item.templateId}`} className="button button-primary">
            加文字
          </Link>
        </MemeTile>
      ))}
    </div>
  );
}
