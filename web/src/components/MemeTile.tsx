import { useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { LibraryItem } from '../api/types';
import { useFavorites } from '../favorites/FavoritesContext';
import { isHttpUrl, sourceTypeLabel } from '../lib/collection';
import { fileNameFor, saveBlob } from '../lib/download';
import { ErrorNotice } from './ErrorNotice';

interface Props {
  item: LibraryItem;
  /** Extra buttons next to download and favorite. */
  children?: ReactNode;
}

/** One meme with the two things people do with it: download it, or keep it among their favorites. */
export function MemeTile({ item, children }: Props) {
  const { isFavorite, add, remove } = useFavorites();
  const favorite = isFavorite(item.templateId);
  const [downloading, setDownloading] = useState(false);
  const [starring, setStarring] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const source = sourceTypeLabel(item.sourceType);

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

  return (
    <article className="card meme-tile">
      <img src={item.imageUrl} alt={item.meaning ?? item.name} loading="lazy" />
      <div className="card-body">
        {item.meaning && <p className="tile-meaning">{item.meaning}</p>}
        {item.tags.length > 0 && (
          <ul className="tag-list" aria-label="標籤">
            {item.tags.slice(0, 6).map((tag) => (
              <li key={tag}>{tag}</li>
            ))}
          </ul>
        )}
        {error != null && <ErrorNotice error={error} />}
        <div className="card-actions">
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
          {children}
        </div>
        {(source || item.attribution) && (
          <span className="muted">
            來源:
            {item.sourceUrl && isHttpUrl(item.sourceUrl) ? (
              <a href={item.sourceUrl} target="_blank" rel="noreferrer">
                {source ?? '原始網址'}
              </a>
            ) : (
              source
            )}
            {item.attribution && `(${item.attribution})`}
          </span>
        )}
      </div>
    </article>
  );
}
