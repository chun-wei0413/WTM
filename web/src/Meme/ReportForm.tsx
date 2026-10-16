import type { ReportReason } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { MAX_COMMENT_LENGTH, REASON_LABELS, REASON_ORDER } from '../lib/reports';
import { useReportForm } from './useReportForm';

interface Props {
  templateId: string;
  onSent: () => void;
  onCancel: () => void;
}

/** A small form under a meme where a user says what is wrong with its description or tags. */
export function ReportForm({ templateId, onSent, onCancel }: Props) {
  const { reason, setReason, comment, setComment, sending, error, tooLong, submit } = useReportForm(templateId, onSent);

  return (
    <form className="report-form" onSubmit={submit}>
      <label>
        哪裡不對?
        <select value={reason} onChange={(e) => setReason(e.target.value as ReportReason)}>
          {REASON_ORDER.map((r) => (
            <option key={r} value={r}>
              {REASON_LABELS[r]}
            </option>
          ))}
        </select>
      </label>
      <label>
        補充說明(選填)
        <textarea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          rows={2}
          placeholder="例如:這只是一張生活照,不是梗圖"
        />
        <span className={tooLong ? 'hint counter field-error' : 'hint counter'}>
          {Array.from(comment).length} / {MAX_COMMENT_LENGTH}
        </span>
      </label>
      {error != null && <ErrorNotice error={error} />}
      <div className="card-actions">
        <button type="submit" className="button button-primary" disabled={sending || tooLong}>
          {sending ? '送出中…' : '送出回報'}
        </button>
        <button type="button" className="button button-quiet" onClick={onCancel} disabled={sending}>
          取消
        </button>
      </div>
    </form>
  );
}
