import { describeUploads, type UploadSummary } from '../../lib/collection';

/** What the library did with the uploaded pictures, and which ones it turned away. */
export function UploadSummaryNotice({ summary, skipped }: { summary: UploadSummary; skipped: number }) {
  return (
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
  );
}
