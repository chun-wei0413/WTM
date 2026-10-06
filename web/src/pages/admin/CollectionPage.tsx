import { useCallback, useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { IndexSyncResult, IngestResult, LibraryStats } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { describeUploads, isHttpUrl, onlyImages, summarizeUploads, type UploadSummary } from '../../lib/collection';

/** Pictures sent per request, so a big folder shows progress and one bad batch does not lose the rest. */
const UPLOAD_BATCH = 20;

export function CollectionPage() {
  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>圖庫收集</h1>
          <p className="lead">
            把梗圖收進圖庫。圖片收進來之後,會由視覺模型自動寫下它的意思、使用時機與圖中文字,標記完成並核准後就能被搜尋到。
          </p>
        </div>
      </div>
      <StatsSection />
      <UploadSection />
      <UrlSection />
    </div>
  );
}

/* ---------------------------------------------------------------- stats */

function StatsSection() {
  const [stats, setStats] = useState<LibraryStats | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [syncing, setSyncing] = useState(false);
  const [synced, setSynced] = useState<IndexSyncResult | null>(null);
  const [syncError, setSyncError] = useState<unknown>(null);

  const refresh = useCallback(async () => {
    try {
      setStats(await api.admin.collection.stats());
      setError(null);
    } catch (e) {
      setError(e);
    }
  }, []);

  // While pictures are still waiting for their tags the numbers keep changing, so keep looking.
  const pending = stats ? stats.waitingForTags + stats.beingTagged : 0;
  useEffect(() => {
    void refresh();
  }, [refresh]);
  useEffect(() => {
    if (pending === 0) return;
    const timer = setInterval(() => void refresh(), 3_000);
    return () => clearInterval(timer);
  }, [pending, refresh]);

  async function sync() {
    setSyncing(true);
    setSyncError(null);
    setSynced(null);
    try {
      setSynced(await api.admin.syncIndex());
      await refresh();
    } catch (e) {
      setSyncError(e);
    } finally {
      setSyncing(false);
    }
  }

  return (
    <section className="section" aria-labelledby="stats-title">
      <div className="section-header">
        <h2 id="stats-title">目前的圖庫</h2>
        <div className="actions">
          <button type="button" className="button" onClick={() => void refresh()}>
            重新整理
          </button>
          <button type="button" className="button" onClick={() => void sync()} disabled={syncing}>
            {syncing ? '同步中…' : '同步搜尋索引'}
          </button>
          <Link to="/admin/templates" className="button">
            管理與核准
          </Link>
        </div>
      </div>

      {error != null && <ErrorNotice error={error} />}
      {stats && (
        <dl className="stats">
          <Stat label="圖庫總數" value={stats.total} />
          <Stat label="可被搜尋(已核准)" value={stats.approved} />
          <Stat label="等待標記" value={stats.waitingForTags} />
          <Stat label="標記中" value={stats.beingTagged} />
          <Stat label="標記失敗" value={stats.tagFailures} warn={stats.tagFailures > 0} />
          <Stat label="已下架" value={stats.retired} />
        </dl>
      )}
      {pending > 0 && (
        <p className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>還有 {pending} 張正在等視覺模型標記,這頁會自動更新。</span>
        </p>
      )}
      {synced && (
        <div className="notice notice-success" role="status">
          搜尋索引已同步:建立或更新 {synced.indexed} 個、移除 {synced.removed} 個
          {synced.failed > 0 && `,有 ${synced.failed} 個失敗(稍後會自動重試)`}。
        </div>
      )}
      {syncError != null && <ErrorNotice error={syncError} />}
    </section>
  );
}

function Stat({ label, value, warn = false }: { label: string; value: number; warn?: boolean }) {
  return (
    <div className={warn ? 'stat stat-warn' : 'stat'}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

/* --------------------------------------------------------------- upload */

function UploadSection() {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [summary, setSummary] = useState<UploadSummary | null>(null);
  const [skipped, setSkipped] = useState(0);
  const [error, setError] = useState<unknown>(null);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const chosen = Array.from(input.files ?? []);
    input.value = ''; // lets the same folder be chosen again
    const files = onlyImages(chosen);
    setSkipped(chosen.length - files.length);
    setSummary(null);
    setError(null);
    if (files.length === 0) return;

    setUploading(true);
    setProgress({ done: 0, total: files.length });
    const results: IngestResult[] = [];
    try {
      for (let i = 0; i < files.length; i += UPLOAD_BATCH) {
        const batch = files.slice(i, i + UPLOAD_BATCH);
        results.push(...(await api.admin.collection.addFiles(batch)));
        setProgress({ done: Math.min(i + batch.length, files.length), total: files.length });
      }
    } catch (e) {
      setError(e);
    } finally {
      // Show what did get in, even when a later batch failed.
      setSummary(summarizeUploads(results));
      setUploading(false);
    }
  }

  return (
    <section className="section" aria-labelledby="upload-title">
      <h2 id="upload-title">從電腦加入</h2>
      <p className="muted">支援 PNG、JPEG、GIF。內容相同或幾乎一樣的圖會自動略過,不會重複收進來。</p>
      <div className="actions">
        <label className={uploading ? 'button file-button disabled' : 'button file-button'}>
          選擇圖片
          <input type="file" accept="image/png,image/jpeg,image/gif" multiple onChange={upload} disabled={uploading} />
        </label>
        <label className={uploading ? 'button file-button disabled' : 'button file-button'}>
          選擇整個資料夾
          <input
            type="file"
            multiple
            onChange={upload}
            disabled={uploading}
            {...({ webkitdirectory: '' } as Record<string, string>)}
          />
        </label>
      </div>

      {uploading && (
        <p className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>
            上傳中… {progress.done} / {progress.total}
          </span>
        </p>
      )}
      {error != null && <ErrorNotice error={error} />}
      {summary && (
        <div className={summary.rejected.length > 0 ? 'notice' : 'notice notice-success'} role="status">
          <strong>{describeUploads(summary)}</strong>
          {skipped > 0 && <span className="notice-detail">另外有 {skipped} 個檔案不是圖片,已略過。</span>}
          {summary.rejected.length > 0 && (
            <ul className="reject-list">
              {summary.rejected.slice(0, 10).map((r) => (
                <li key={r.fileName}>
                  {r.fileName}
                  {r.reason && <span className="muted">:{r.reason}</span>}
                </li>
              ))}
              {summary.rejected.length > 10 && <li className="muted">…還有 {summary.rejected.length - 10} 個</li>}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}

/* ------------------------------------------------------------------ URL */

function UrlSection() {
  const [url, setUrl] = useState('');
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: 'success' | 'info' | 'warn'; text: string } | null>(null);
  const [error, setError] = useState<unknown>(null);

  const valid = isHttpUrl(url);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const result = await api.admin.collection.addUrl(url.trim(), title.trim() || undefined);
      if (result.status === 'IMPORTED') {
        setMessage({ kind: 'success', text: '已加入圖庫,稍後會自動標記。' });
        setUrl('');
        setTitle('');
      } else if (result.status === 'DUPLICATE') {
        setMessage({ kind: 'info', text: '這張圖已經在圖庫裡了。' });
      } else {
        setMessage({ kind: 'warn', text: `這個網址的圖片不能用${result.reason ? `:${result.reason}` : ''}` });
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="section" aria-labelledby="url-title">
      <h2 id="url-title">貼上圖片網址</h2>
      <form onSubmit={submit} className="form url-form">
        <label>
          圖片網址(直接指向圖片檔)
          <input
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://example.com/meme.jpg"
            required
          />
        </label>
        <label>
          名稱(可留空,留空會依圖片內容自動命名)
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} />
        </label>
        <div>
          <button type="submit" className="button button-primary" disabled={busy || !valid}>
            {busy ? '加入中…' : '加入圖庫'}
          </button>
        </div>
      </form>
      {error != null && <ErrorNotice error={error} />}
      {message && (
        <div className={message.kind === 'success' ? 'notice notice-success' : 'notice'} role="status">
          {message.text}
        </div>
      )}
    </section>
  );
}
