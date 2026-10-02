import { useState } from 'react';
import { api } from '../api/client';
import type { Captions } from '../api/types';
import { fileNameFor, saveBlob } from '../lib/download';
import { ErrorNotice } from './ErrorNotice';

interface Props {
  memeId: string;
  imageUrl: string | null;
  templateName: string;
  captions: Captions;
  kept: boolean;
  /** Present when the meme can still be kept; omitted on the "my memes" page. */
  onKeep?: () => Promise<void>;
}

function orderedCaptions(captions: Captions): Array<[string, string]> {
  return Object.entries(captions).sort(([a], [b]) => Number(a) - Number(b));
}

export function MemeCard({ memeId, imageUrl, templateName, captions, kept, onKeep }: Props) {
  const [keeping, setKeeping] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const lines = orderedCaptions(captions);

  async function keep() {
    if (!onKeep) return;
    setKeeping(true);
    setError(null);
    try {
      await onKeep();
    } catch (e) {
      setError(e);
    } finally {
      setKeeping(false);
    }
  }

  async function download() {
    setDownloading(true);
    setError(null);
    try {
      const blob = await api.downloadMeme(memeId);
      saveBlob(blob, fileNameFor(memeId, blob));
    } catch (e) {
      setError(e);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <article className="card meme-card">
      {imageUrl ? (
        <img src={imageUrl} alt={`使用「${templateName}」模板做出的梗圖`} loading="lazy" />
      ) : (
        <div className="image-missing">沒有圖片</div>
      )}
      <div className="card-body">
        <div className="card-title">
          <span>{templateName}</span>
          {kept && <span className="badge badge-success">已留下</span>}
        </div>
        {lines.length > 0 && (
          <ol className="captions">
            {lines.map(([slot, text]) => (
              <li key={slot}>{text}</li>
            ))}
          </ol>
        )}
        {error != null && <ErrorNotice error={error} />}
        <div className="card-actions">
          {onKeep && !kept && (
            <button type="button" className="button button-primary" onClick={keep} disabled={keeping}>
              {keeping ? '儲存中…' : '留下這張'}
            </button>
          )}
          <button type="button" className="button" onClick={download} disabled={downloading || !imageUrl}>
            {downloading ? '下載中…' : '下載'}
          </button>
        </div>
      </div>
    </article>
  );
}
