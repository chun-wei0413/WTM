import { useState, type ReactNode } from 'react';
import { api } from '../api/client';
import type { LibraryItem } from '../api/types';
import { useFavorites } from '../favorites/FavoritesContext';
import { attributionNote, isHttpUrl, sourceTypeLabel } from '../lib/collection';
import { fileNameFor, saveBlob } from '../lib/download';
import { ErrorNotice } from './ErrorNotice';
import { ReportForm } from './ReportForm';

interface Props {
  item: LibraryItem;
  /** Extra buttons next to download and favorite. */
  children?: ReactNode;
}

/** One meme: the picture, the two things people do with it (download, favorite), and the rest behind "詳情". */
export function MemeTile({ item, children }: Props) {
  const { isFavorite, add, remove } = useFavorites();
  const favorite = isFavorite(item.templateId);
  const [downloading, setDownloading] = useState(false);
  const [starring, setStarring] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [reporting, setReporting] = useState(false);
  const [reported, setReported] = useState(false);
  const source = sourceTypeLabel(item.sourceType);
  const note = attributionNote(source, item.attribution);

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
      <div className="tile-bar">
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
        <button
          type="button"
          className="button button-quiet tile-more"
          onClick={() => setDetailsOpen((open) => !open)}
          aria-expanded={detailsOpen}
        >
          {detailsOpen ? '收起' : '詳情'}
        </button>
      </div>
      {error != null && (
        <div className="tile-error">
          <ErrorNotice error={error} />
        </div>
      )}
      {detailsOpen && (
        <div className="tile-details">
          {item.meaning && <p className="tile-meaning">{item.meaning}</p>}
          {item.tags.length > 0 && (
            <ul className="tag-list" aria-label="標籤">
              {item.tags.slice(0, 8).map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
            </ul>
          )}
          {(source || note) && (
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
          )}
          <div>
            <button
              type="button"
              className="button button-quiet"
              onClick={() => setReporting((open) => !open)}
              disabled={reported}
              aria-expanded={reporting}
              title="標籤、描述不精確,或這不是梗圖?告訴管理員"
            >
              {reported ? '已回報' : '回報問題'}
            </button>
          </div>
          {reporting && !reported && (
            <ReportForm
              templateId={item.templateId}
              onSent={() => {
                setReported(true);
                setReporting(false);
              }}
              onCancel={() => setReporting(false)}
            />
          )}
          {reported && (
            <p className="notice notice-success" role="status">
              已回報,謝謝你!管理員會查看,並請影像模型重新看這張圖。
            </p>
          )}
        </div>
      )}
    </article>
  );
}
