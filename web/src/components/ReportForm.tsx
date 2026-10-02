import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { ReportReason } from '../api/types';
import { MAX_COMMENT_LENGTH, REASON_LABELS, REASON_ORDER, commentTooLong } from '../lib/reports';
import { ErrorNotice } from './ErrorNotice';

interface Props {
  templateId: string;
  onSent: () => void;
  onCancel: () => void;
}

/** A small form under a meme where a user says what is wrong with its description or tags. */
export function ReportForm({ templateId, onSent, onCancel }: Props) {
  const [reason, setReason] = useState<ReportReason>('WRONG_TAGS');
  const [comment, setComment] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const tooLong = commentTooLong(comment);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (tooLong) return;
    setSending(true);
    setError(null);
    try {
      await api.reports.submit(templateId, reason, comment.trim());
      onSent();
    } catch (e) {
      setError(e);
      setSending(false);
    }
  }

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
