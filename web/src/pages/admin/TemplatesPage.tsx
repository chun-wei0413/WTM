import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { IndexSyncResult, TemplateStatus, TemplateSummary } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { StatusBadge } from '../../components/StatusBadge';

type Load = { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; templates: TemplateSummary[] };

const FILTERS: Array<{ value: TemplateStatus | ''; label: string }> = [
  { value: '', label: '全部' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'APPROVED', label: '已核准' },
  { value: 'RETIRED', label: '已下架' },
];

export function TemplatesPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<TemplateStatus | ''>('');
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

  useEffect(() => {
    let cancelled = false;
    setLoad({ state: 'loading' });
    api.admin
      .listTemplates(filter || undefined)
      .then((templates) => !cancelled && setLoad({ state: 'ready', templates }))
      .catch((error: unknown) => !cancelled && setLoad({ state: 'error', error }));
    return () => {
      cancelled = true;
    };
  }, [filter]);

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
          <h1>模板管理</h1>
          <p className="lead">上傳梗圖模板、標出文字要放的位置,並描述這個梗的意思。核准之後,使用者才找得到它。</p>
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

      <div className="tabs" role="tablist" aria-label="狀態篩選">
        {FILTERS.map((f) => (
          <button key={f.value} type="button" role="tab" aria-selected={filter === f.value} onClick={() => setFilter(f.value)}>
            {f.label}
          </button>
        ))}
      </div>

      {load.state === 'loading' && <p className="status">載入中…</p>}
      {load.state === 'error' && <ErrorNotice error={load.error} />}
      {load.state === 'ready' && load.templates.length === 0 && (
        <div className="empty">
          <p>這裡還沒有模板。</p>
        </div>
      )}
      {load.state === 'ready' && load.templates.length > 0 && (
        <div className="grid grid-small">
          {load.templates.map((t) => (
            <Link key={t.id} to={`/admin/templates/${t.id}`} className="card template-card">
              <img src={t.imageUrl} alt="" loading="lazy" />
              <div className="card-body">
                <div className="card-title">
                  <span>{t.name}</span>
                  <StatusBadge status={t.status} />
                </div>
                <span className="muted">版本 {t.version}</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
