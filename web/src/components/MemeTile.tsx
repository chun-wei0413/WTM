import { useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { LibraryItem } from '../api/types';
import { useFavorites } from '../favorites/FavoritesContext';
import { fileNameFor, saveBlob } from '../lib/download';
import { ErrorNotice } from './ErrorNotice';
import { MemeDetails } from './MemeDetails';
import { MemeViewer } from './MemeViewer';

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
  const { isFavorite, add, remove } = useFavorites();
  const favorite = isFavorite(item.templateId);
  const [downloading, setDownloading] = useState(false);
  const [starring, setStarring] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [viewing, setViewing] = useState(false);
  const [reported, setReported] = useState(false);

  async function download() {
    setDownloading(true);
    setError(null);
    try {
      const blob = await api.library.image(item.templateId);
      saveBlob(blob, fileNameFor(`meme-${item.templateId.slice(0, 8)}`, blob));
    } catch (e) {
      setError(e);
    } finally {
      setDownloading(false);
    }
  }

  async function toggleFavorite() {
    setStarring(true);
    setError(null);
    try {
      await (favorite ? remove(item.templateId) : add(item));
    } catch (e) {
      setError(e);
    } finally {
      setStarring(false);
    }
  }

  const actions = (
    <>
      <button type="button" className="button" onClick={download} disabled={downloading}>
        {downloading ? '下載中…' : '下載'}
      </button>
      <button
        type="button"
        className={favorite ? 'button button-favorite active' : 'button button-favorite'}
        onClick={toggleFavorite}
        disabled={starring}
        aria-pressed={favorite}
      >
        {favorite ? '★ 已收藏' : '☆ 收藏'}
      </button>
    </>
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
            <button type="button" className="tile-meaning-button" onClick={() => setViewing(true)} title="看完整說明">
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
        <div className="tile-details">
          {!pop && item.meaning && <p className="tile-meaning">{item.meaning}</p>}
          {!pop && item.tags.length > 0 && (
            <ul className="tag-list" aria-label="標籤">
              {item.tags.slice(0, 8).map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
            </ul>
          )}
          {details}
        </div>
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
