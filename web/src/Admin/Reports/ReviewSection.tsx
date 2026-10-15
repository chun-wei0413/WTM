import type { ReportCase } from '../../api/types';
import { REVIEW_LABELS } from '../../lib/reports';
import { reviewState } from './caseReview';
import { Comparison } from './Comparison';

/** How far the vision model's second look at a meme has come, and what it proposes. */
export function ReviewSection({ item }: { item: ReportCase }) {
  const { review, working, suggestion } = reviewState(item);
  return (
    <section>
      <h3>影像模型重新分析</h3>
      {review === null && (
        <p className="muted">
          還沒有分析。目前回報的可信度合計 {item.weight} / {item.neededWeight},人數還不夠,系統不會自動花模型時間;
          你可以按「重新分析」手動分析。
        </p>
      )}
      {working && review && (
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
  );
}
