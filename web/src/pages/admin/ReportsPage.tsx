import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api/client';
import type { ReportCase } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { REASON_LABELS, REPORT_POLL_MS, REVIEW_LABELS, diffList, isReviewing, sameText } from '../../lib/reports';

type Load = { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; cases: ReportCase[] };

export function ReportsPage() {
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  const refresh = useCallback(async () => {
    try {
      setLoad({ state: 'ready', cases: await api.admin.reports.list() });
    } catch (error) {
      setLoad((previous) => (previous.state === 'ready' ? previous : { state: 'error', error }));
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // While the vision model is still working on a case, look again every few seconds.
  const reviewing = load.state === 'ready' && isReviewing(load.cases);
  useEffect(() => {
    if (!reviewing) return;
    const timer = setInterval(() => void refresh(), REPORT_POLL_MS);
    return () => clearInterval(timer);
  }, [reviewing, refresh]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>意見回報</h1>
          <p className="lead">
            使用者覺得標籤或描述不精確的梗圖。每一張都已請影像模型帶著使用者的意見重新看過一遍,並提出新的描述,
            由你決定要不要採用。
          </p>
        </div>
        <button type="button" className="button" onClick={() => void refresh()}>
          重新整理
        </button>
      </div>

      {load.state === 'loading' && <p className="status">載入中…</p>}
      {load.state === 'error' && <ErrorNotice error={load.error} />}
      {load.state === 'ready' && load.cases.length === 0 && (
        <div className="empty">
          <p>目前沒有待處理的回報。</p>
        </div>
      )}
      {load.state === 'ready' &&
        load.cases.map((c) => <CaseCard key={c.templateId} item={c} onChanged={() => void refresh()} />)}
    </div>
  );
}

function CaseCard({ item, onChanged }: { item: ReportCase; onChanged: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const review = item.review;
  const working = review?.status === 'PENDING' || review?.status === 'RUNNING';
  const suggestion = review?.status === 'DONE' ? review.suggestion : null;

  async function run(label: string, action: () => Promise<unknown>) {
    setBusy(label);
    setError(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  function apply() {
    if (suggestion && !suggestion.isMeme && !window.confirm('模型認為這不是梗圖。確定還是要套用它的描述嗎?')) return;
    void run('apply', () => api.admin.reports.apply(item.templateId));
  }

  function retire() {
    if (!window.confirm('下架後這張梗圖不會再被搜尋到,所有回報也會一併結案。確定嗎?')) return;
    void run('retire', async () => {
      await api.admin.retire(item.templateId);
      await api.admin.reports.dismiss(item.templateId);
    });
  }

  return (
    <article className="card case-card">
      <div className="case-image">
        <img src={item.imageUrl} alt={item.current.meaning || item.name} loading="lazy" />
        <strong>{item.name}</strong>
        <Link to={`/admin/templates/${item.templateId}`} className="muted">
          開啟編輯頁
        </Link>
      </div>

      <div className="case-body">
        <section>
          <h3>用戶回報({item.reports.length})</h3>
          <ul className="report-list">
            {item.reports.map((r) => (
              <li key={r.id}>
                <span className="badge badge-accent">{REASON_LABELS[r.reason]}</span>{' '}
                {r.comment ? <span>用戶報告:{r.comment}</span> : <span className="muted">(沒有補充說明)</span>}
                <span className="muted">
                  {' '}
                  — {r.username} · {new Date(r.createdAt).toLocaleString('zh-TW')}
                </span>
              </li>
            ))}
          </ul>
        </section>

        <section>
          <h3>影像模型重新分析</h3>
          {review === null && <p className="muted">還沒有分析。</p>}
          {working && (
            <p className="status status-working" role="status">
              <span className="spinner" aria-hidden="true" />
              <span>{REVIEW_LABELS[review.status]}…(通常要一分鐘左右)</span>
            </p>
          )}
          {review?.status === 'FAILED' && (
            <div className="notice notice-error" role="alert">
              <strong>分析失敗</strong>
              {review.error && <span className="notice-detail">{review.error}</span>}
            </div>
          )}
          {suggestion && (
            <>
              {!suggestion.isMeme && (
                <p className="notice notice-error" role="alert">
                  模型認為這張圖不是梗圖。
                </p>
              )}
              <p className="suggestion-reason">
                <strong>修改建議:</strong>
                {suggestion.reasoning || '(模型沒有說明)'}
              </p>
              <Comparison item={item} suggestion={suggestion} />
            </>
          )}
        </section>

        {error != null && <ErrorNotice error={error} />}
        <div className="card-actions">
          <button
            type="button"
            className="button button-primary"
            onClick={apply}
            disabled={!suggestion || busy !== null}
            title={suggestion ? undefined : '要等模型分析完才能套用'}
          >
            {busy === 'apply' ? '套用中…' : '套用 AI 建議'}
          </button>
          <button
            type="button"
            className="button"
            onClick={() => void run('dismiss', () => api.admin.reports.dismiss(item.templateId))}
            disabled={busy !== null}
          >
            {busy === 'dismiss' ? '處理中…' : '忽略回報'}
          </button>
          <button
            type="button"
            className="button"
            onClick={() => void run('reanalyze', () => api.admin.reports.reanalyze(item.templateId))}
            disabled={busy !== null || working}
          >
            重新分析
          </button>
          <button type="button" className="button button-danger" onClick={retire} disabled={busy !== null}>
            下架這張
          </button>
        </div>
      </div>
    </article>
  );
}

/** The current description and the proposal side by side, with what changed marked. */
function Comparison({ item, suggestion }: { item: ReportCase; suggestion: NonNullable<ReportCase['review']>['suggestion'] }) {
  if (!suggestion) return null;
  const current = item.current;
  const tags = diffList(current.tags, suggestion.tags);
  const usage = diffList(current.usageExamples, suggestion.usageExamples);
  const meaningChanged = !sameText(current.meaning, suggestion.meaning);
  const textChanged = !sameText(current.imageText, suggestion.imageText);

  return (
    <div className="compare">
      <div className="compare-col">
        <h4>目前的描述</h4>
        <Field label="意思" value={current.meaning} />
        <Field label="使用情境" list={current.usageExamples} />
        <div>
          <span className="field-label">標籤</span>
          <ul className="tag-list">
            {current.tags.map((tag) => (
              <li key={tag} className={tags.removed.includes(tag) ? 'tag-removed' : undefined}>
                {tag}
              </li>
            ))}
          </ul>
        </div>
        <Field label="圖中文字" value={current.imageText} />
      </div>
      <div className="compare-col compare-new">
        <h4>AI 的建議</h4>
        <Field label="意思" value={suggestion.meaning} changed={meaningChanged} />
        <Field label="使用情境" list={suggestion.usageExamples} added={usage.added} />
        <div>
          <span className="field-label">標籤</span>
          <ul className="tag-list">
            {suggestion.tags.map((tag) => (
              <li key={tag} className={tags.added.includes(tag) ? 'tag-added' : undefined}>
                {tag}
              </li>
            ))}
          </ul>
        </div>
        <Field label="圖中文字" value={suggestion.imageText} changed={textChanged} />
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  list,
  changed = false,
  added = [],
}: {
  label: string;
  value?: string;
  list?: string[];
  changed?: boolean;
  added?: string[];
}) {
  return (
    <div className={changed ? 'field field-changed' : 'field'}>
      <span className="field-label">{label}</span>
      {list ? (
        list.length === 0 ? (
          <span className="muted">(空)</span>
        ) : (
          <ul>
            {list.map((line) => (
              <li key={line} className={added.includes(line) ? 'line-added' : undefined}>
                {line}
              </li>
            ))}
          </ul>
        )
      ) : (
        <p>{value || <span className="muted">(空)</span>}</p>
      )}
    </div>
  );
}
