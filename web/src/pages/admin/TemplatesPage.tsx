import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { IndexSyncResult, TemplateSummary } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { StatusBadge } from '../../components/StatusBadge';
import { attributionNote, sourceTypeLabel } from '../../lib/collection';
import { countByStatus, filterTemplates, type StatusFilter } from '../../lib/templateList';

type Load = { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; templates: TemplateSummary[] };

const TABS: Array<{ value: StatusFilter; label: string }> = [
  { value: 'APPROVED', label: '已核准' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'RETIRED', label: '已下架' },
  { value: 'ALL', label: '全部' },
];

/** How many entries are drawn at first, and added each time "show more" is pressed. */
const PAGE = 48;

export function TemplatesPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<StatusFilter>('APPROVED');
  const [query, setQuery] = useState('');
  const [shown, setShown] = useState(PAGE);
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  const [adding, setAdding] = useState(false);
  const [name, setName] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  const [syncing, setSyncing] = useState(false);
  const [synced, setSynced] = useState<IndexSyncResult | null>(null);
  const [syncError, setSyncError] = useState<unknown>(null);

  // The whole list is fetched once; choosing a tab or typing a search then answers at once.
  useEffect(() => {
    let cancelled = false;
    api.admin
      .listTemplates()
      .then((templates) => !cancelled && setLoad({ state: 'ready', templates }))
      .catch((error: unknown) => !cancelled && setLoad({ state: 'error', error }));
    return () => {
      cancelled = true;
    };
  }, []);

  const all = load.state === 'ready' ? load.templates : [];
  const counts = useMemo(() => countByStatus(all), [all]);
  const matching = useMemo(() => filterTemplates(all, status, query), [all, status, query]);

  // Release the preview image when it is replaced or the page closes.
  useEffect(() => {
    if (!file) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function choose(next: StatusFilter) {
    setStatus(next);
    setShown(PAGE);
  }

  function search(text: string) {
    setQuery(text);
    setShown(PAGE);
  }

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] ?? null;
    setFile(chosen);
    if (chosen && !name.trim()) setName(chosen.name.replace(/\.[^.]+$/, ''));
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    setCreating(true);
    setCreateError(null);
    try {
      const { id } = await api.admin.draftTemplate(name.trim(), file);
      navigate(`/admin/templates/${id}`);
    } catch (e) {
      setCreateError(e);
      setCreating(false);
    }
  }

  async function syncIndex() {
    setSyncing(true);
    setSyncError(null);
    setSynced(null);
    try {
      setSynced(await api.admin.syncIndex());
    } catch (e) {
      setSyncError(e);
    } finally {
      setSyncing(false);
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>圖庫管理</h1>
          <p className="lead">看每一張圖是什麼、改它的描述與標籤、核准或下架。核准之後,使用者才找得到它。</p>
        </div>
        <div className="actions">
          <button type="button" className="button" onClick={syncIndex} disabled={syncing}>
            {syncing ? '同步中…' : '同步搜尋索引'}
          </button>
          <button type="button" className="button button-primary" onClick={() => setAdding((v) => !v)}>
            {adding ? '取消' : '新增模板'}
          </button>
        </div>
      </div>

      {synced && (
        <div className="notice notice-success" role="status">
          搜尋索引已同步:建立或更新 {synced.indexed} 個、移除 {synced.removed} 個
          {synced.failed > 0 && `,有 ${synced.failed} 個失敗(稍後會自動重試)`}。
        </div>
      )}
      {syncError != null && <ErrorNotice error={syncError} />}

      {adding && (
        <form onSubmit={create} className="card form new-template">
          <h2>新增模板</h2>
          <label>
            名稱
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
          </label>
          <label>
            圖片(PNG 或 JPEG,不含文字的原圖最佳)
            <input type="file" accept="image/png,image/jpeg" onChange={onFile} required />
          </label>
          {preview && <img className="upload-preview" src={preview} alt="預覽" />}
          {createError != null && <ErrorNotice error={createError} />}
          <div>
            <button type="submit" className="button button-primary" disabled={creating || !file || !name.trim()}>
              {creating ? '上傳中…' : '建立草稿並開始編輯'}
            </button>
          </div>
        </form>
      )}

      <div className="list-tools">
        <div className="tabs" role="tablist" aria-label="狀態篩選">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              role="tab"
              aria-selected={status === tab.value}
              onClick={() => choose(tab.value)}
            >
              {tab.label}
              {load.state === 'ready' && <span className="tab-count">{counts[tab.value]}</span>}
            </button>
          ))}
        </div>
        <input
          type="search"
          className="list-search"
          value={query}
          onChange={(e) => search(e.target.value)}
          placeholder="搜尋名稱、意思、標籤、來源"
          aria-label="搜尋圖庫"
        />
      </div>

      {load.state === 'loading' && <p className="status">載入中…</p>}
      {load.state === 'error' && <ErrorNotice error={load.error} />}
      {load.state === 'ready' && matching.length === 0 && (
        <div className="empty">
          <p>{query.trim() ? '沒有符合的圖。' : '這裡還沒有圖。'}</p>
        </div>
      )}
      {load.state === 'ready' && matching.length > 0 && (
        <>
          <p className="muted">
            {matching.length} 張{matching.length > shown && `,先顯示 ${shown} 張`}
          </p>
          <div className="grid grid-small">
            {matching.slice(0, shown).map((t) => (
              <Link
                key={t.id}
                to={`/admin/templates/${t.id}`}
                className={t.status === 'RETIRED' ? 'card template-card picker-card retired' : 'card template-card picker-card'}
              >
                <img src={t.imageUrl} alt="" loading="lazy" />
                <div className="card-body">
                  <div className="card-title">
                    <span>{t.name}</span>
                    <StatusBadge status={t.status} />
                  </div>
                  {t.meaning && <p className="tile-meaning">{t.meaning}</p>}
                  <span className="muted">
                    {sourceTypeLabel(t.sourceType) ?? '手動建立'}
                    {attributionNote(sourceTypeLabel(t.sourceType), t.attribution) &&
                      ` · ${attributionNote(sourceTypeLabel(t.sourceType), t.attribution)}`}
                  </span>
                </div>
              </Link>
            ))}
          </div>
          {matching.length > shown && (
            <div className="actions">
              <button type="button" className="button" onClick={() => setShown((n) => n + PAGE)}>
                顯示更多(還有 {matching.length - shown} 張)
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
