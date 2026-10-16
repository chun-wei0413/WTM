import type { LibraryItem } from '../api/types';
import { attributionNote, isHttpUrl, sourceTypeLabel } from '../lib/collection';

/** Where the picture was collected from, with a link when the address is a web address; nothing when unknown. */
export function MemeSource({ item }: { item: LibraryItem }) {
  const source = sourceTypeLabel(item.sourceType);
  const note = attributionNote(source, item.attribution);
  if (!source && !note) return null;

  return (
    <span className="muted">
      來源:
      {item.sourceUrl && isHttpUrl(item.sourceUrl) ? (
        <a href={item.sourceUrl} target="_blank" rel="noreferrer">
          {source ?? '原始網址'}
        </a>
      ) : (
        source
      )}
      {note && `(${note})`}
    </span>
  );
}
