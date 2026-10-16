import { useState, type ReactNode } from 'react';
import type { LibraryItem } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { MemeDetails } from './MemeDetails';
import { MemeViewer } from './MemeViewer';
import { TileActions } from './TileActions';
import { TileCaption } from './TileCaption';
import { TileDetails } from './TileDetails';
import { useTileActions } from './useTileActions';

interface Props {
  item: LibraryItem;
  /** Extra buttons next to download and favorite. */
  children?: ReactNode;
  /** Playful layout: the meaning and tags sit under the picture, numbered from 1. */
  index?: number;
  /** With `index`: the first result, set larger beside its caption. */
  lead?: boolean;
  /** With `index`: words in place of the number, for the meme that was picked. */
  badge?: string;
  /** With `index`: why this meme fits, shown as a speech bubble above its description. */
  reason?: string;
}

/**
 * One meme: the picture, the two things people do with it (download, favorite), and the rest behind "詳情".
 * Clicking the picture or its description opens it large, with the description in full.
 */
export function MemeTile({ item, children, index, lead, badge, reason }: Props) {
  const pop = index !== undefined;
  const { favorite, downloading, starring, error, download, toggleFavorite } = useTileActions(item);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [viewing, setViewing] = useState(false);
  const [reported, setReported] = useState(false);

  const actions = (
    <TileActions
      favorite={favorite}
      downloading={downloading}
      starring={starring}
      onDownload={download}
      onToggleFavorite={toggleFavorite}
    />
  );
  const details = <MemeDetails item={item} reported={reported} onReported={() => setReported(true)} />;

  return (
    <article
      className={pop ? `meme-tile tile-pop${lead ? ' tile-lead' : ''}${badge ? ' tile-picked' : ''}` : 'card meme-tile'}
    >
      <button type="button" className="tile-picture" onClick={() => setViewing(true)} aria-label={`放大看「${item.name}」`}>
        <img
          src={item.imageUrl}
          alt={item.meaning ?? item.name}
          width={item.imageWidth}
          height={item.imageHeight}
          loading={lead ? 'eager' : 'lazy'}
        />
      </button>
      {pop && (
        <TileCaption item={item} index={index} lead={lead} badge={badge} reason={reason} onOpen={() => setViewing(true)} />
      )}
      <div className="tile-bar">
        {actions}
        {children}
        <button
          type="button"
          className="button button-quiet tile-more"
          onClick={() => setDetailsOpen((open) => !open)}
          aria-expanded={detailsOpen}
        >
          {detailsOpen ? '收起' : pop ? '來源・回報' : '詳情'}
        </button>
      </div>
      {error != null && (
        <div className="tile-error">
          <ErrorNotice error={error} />
        </div>
      )}
      {detailsOpen && (
        <TileDetails item={item} pop={pop}>
          {details}
        </TileDetails>
      )}
      {viewing && (
        <MemeViewer
          item={item}
          onClose={() => setViewing(false)}
          actions={actions}
          details={details}
          notice={error != null && <ErrorNotice error={error} />}
        />
      )}
    </article>
  );
}
