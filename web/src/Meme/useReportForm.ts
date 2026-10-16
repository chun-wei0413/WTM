import { useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { ReportReason } from '../api/types';
import { commentTooLong } from '../lib/reports';

/** What a user says is wrong with a meme: a reason and a comment, sent to the administrators. */
export function useReportForm(templateId: string, onSent: () => void) {
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

  return { reason, setReason, comment, setComment, sending, error, tooLong, submit };
}
