import { Link } from 'react-router-dom';
import type { ReportCase } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { reviewState } from './caseReview';
import { ReportList } from './ReportList';
import { ReviewSection } from './ReviewSection';
import { useCaseActions } from './useCaseActions';

/** One reported meme: the complaints, the model's second look, and what the administrator can do about it. */
export function CaseCard({ item, onChanged }: { item: ReportCase; onChanged: () => void }) {
  const { busy, error, apply, dismiss, reanalyze, retire } = useCaseActions(item, onChanged);
  const { working, suggestion } = reviewState(item);

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
        <ReportList reports={item.reports} />
        <ReviewSection item={item} />

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
          <button type="button" className="button" onClick={dismiss} disabled={busy !== null}>
            {busy === 'dismiss' ? '處理中…' : '忽略回報'}
          </button>
          <button type="button" className="button" onClick={reanalyze} disabled={busy !== null || working}>
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
