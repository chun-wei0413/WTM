import { useCallback, useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { CollectionRun, CollectionSource, IndexSyncResult, IngestResult, LibraryStats } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import {
  RUN_STATUS_LABELS,
  changedOptions,
  describeUploads,
  isHttpUrl,
  onlyImages,
  runListDelay,
  sourceTypeLabel,
  summarizeUploads,
  type UploadSummary,
} from '../../lib/collection';

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
      <SourceSection />
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

/* -------------------------------------------------------------- sources */

type Sources = { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; sources: CollectionSource[] };

function SourceSection() {
  const [sources, setSources] = useState<Sources>({ state: 'loading' });
  const [runs, setRuns] = useState<CollectionRun[]>([]);
  const [runsError, setRunsError] = useState<unknown>(null);

  const [sourceId, setSourceId] = useState('');
  const [limit, setLimit] = useState(20);
  const [values, setValues] = useState<Record<string, string>>({});
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<unknown>(null);

  const loadRuns = useCallback(async () => {
    try {
      const list = await api.admin.collection.runs();
      setRuns(list);
      setRunsError(null);
      return list;
    } catch (e) {
      setRunsError(e);
      return null;
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    api.admin.collection
      .sources()
      .then((list) => {
        if (cancelled) return;
        setSources({ state: 'ready', sources: list });
        setSourceId((current) => current || list[0]?.id || '');
      })
      .catch((error: unknown) => !cancelled && setSources({ state: 'error', error }));
    return () => {
      cancelled = true;
    };
  }, []);

  // Look at the run list now, and again every couple of seconds for as long as something is running.
  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const tick = async () => {
      const list = await loadRuns();
      if (cancelled) return;
      const delay = list ? runListDelay(list) : 5_000;
      if (delay !== null) timer = setTimeout(() => void tick(), delay);
    };
    void tick();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [loadRuns, starting]);

  const chosen = sources.state === 'ready' ? sources.sources.find((s) => s.id === sourceId) : undefined;
  const running = runs.some((r) => r.status === 'RUNNING');

  function pickSource(id: string) {
    setSourceId(id);
    setValues({});
  }

  async function start(event: FormEvent) {
    event.preventDefault();
    if (!chosen) return;
    setStarting(true);
    setStartError(null);
    try {
      await api.admin.collection.startRun(chosen.id, limit, changedOptions(chosen.options, values));
    } catch (e) {
      setStartError(e);
    } finally {
      setStarting(false); // also restarts the run list polling above
    }
  }

  return (
    <section className="section" aria-labelledby="source-title">
      <h2 id="source-title">從網路來源收集</h2>
      <p className="muted">在背景自動抓取,一次只能進行一個。請遵守各網站的使用條款;每張圖的來源與授權說明都會一併記下來。</p>

      {sources.state === 'loading' && <p className="status">載入中…</p>}
      {sources.state === 'error' && <ErrorNotice error={sources.error} />}
      {sources.state === 'ready' && sources.sources.length === 0 && <p className="muted">目前沒有可用的來源。</p>}

      {chosen && (
        <form onSubmit={start} className="form source-form">
          <label>
            來源
            <select value={sourceId} onChange={(e) => pickSource(e.target.value)} disabled={running || starting}>
              {sources.state === 'ready' &&
                sources.sources.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
            </select>
            <span className="hint">{chosen.description}</span>
          </label>
          <label>
            最多收幾張
            <input
              type="number"
              min={1}
              max={500}
              value={limit}
              onChange={(e) => setLimit(Number(e.target.value))}
              disabled={running || starting}
            />
          </label>
          {chosen.options.map((option) => (
            <label key={`${chosen.id}-${option.key}`}>
              {option.label}
              <input
                value={values[option.key] ?? option.defaultValue}
                onChange={(e) => setValues((previous) => ({ ...previous, [option.key]: e.target.value }))}
                disabled={running || starting}
              />
            </label>
          ))}
          <div>
            <button
              type="submit"
              className="button button-primary"
              disabled={running || starting || !Number.isInteger(limit) || limit < 1}
            >
              {starting ? '送出中…' : running ? '已有收集在進行中' : '開始收集'}
            </button>
          </div>
        </form>
      )}
      {startError != null && <ErrorNotice error={startError} />}

      <h3>最近的收集紀錄</h3>
      {runsError != null && <ErrorNotice error={runsError} />}
      {runs.length === 0 && runsError == null && <p className="muted">還沒有收集紀錄。</p>}
      {runs.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>來源</th>
                <th>狀態</th>
                <th>找到</th>
                <th>新增</th>
                <th>重複</th>
                <th>不可用</th>
                <th>失敗</th>
                <th>開始時間</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <RunRow key={run.id} run={run} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function RunRow({ run }: { run: CollectionRun }) {
  const badge = run.status === 'COMPLETED' ? 'badge badge-success' : run.status === 'FAILED' ? 'badge badge-danger' : 'badge badge-accent';
  return (
    <tr>
      <td>{sourceTypeLabel(run.source) ?? run.source}</td>
      <td>
        <span className={badge}>{RUN_STATUS_LABELS[run.status]}</span>
        {run.message && <div className="muted run-message">{run.message}</div>}
      </td>
      <td>{run.counts.found}</td>
      <td>{run.counts.imported}</td>
      <td>{run.counts.duplicates}</td>
      <td>{run.counts.rejected}</td>
      <td>{run.counts.failed}</td>
      <td>
        <time dateTime={run.startedAt}>{new Date(run.startedAt).toLocaleString('zh-TW')}</time>
      </td>
    </tr>
  );
}
