import type { LibraryItem } from '../api/types';

interface Props {
  item: LibraryItem;
  /** The number of the meme in the list, from 1. */
  index: number;
  /** The first result, which shows more tags. */
  lead?: boolean;
  /** Words in place of the number, for the meme that was picked. */
  badge?: string;
  /** Why this meme fits, shown as a speech bubble. */
  reason?: string;
  /** Opens the meme large. */
  onOpen: () => void;
}

/** What sits under the picture in the playful layout: the number, why it fits, the meaning and the tags. */
export function TileCaption({ item, index, lead, badge, reason, onOpen }: Props) {
  return (
    <div className="tile-caption">
      <span className={badge ? 'tile-no tile-no-word' : 'tile-no'} aria-hidden={badge ? undefined : true}>
        {badge ?? String(index).padStart(2, '0')}
      </span>
      {reason && (
        <div className="tile-reason">
          <span className="tile-reason-label">我的看法</span>
          <p>{reason}</p>
          <p className="tile-reason-hint">AI 寫的,不一定準</p>
        </div>
      )}
      {item.meaning && (
        <button type="button" className="tile-meaning-button" onClick={onOpen} title="看完整說明">
          <span className="tile-meaning">{item.meaning}</span>
        </button>
      )}
      {item.tags.length > 0 && (
        <ul className="tag-list" aria-label="標籤">
          {item.tags.slice(0, lead ? 8 : 4).map((tag) => (
            <li key={tag}>{tag}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
