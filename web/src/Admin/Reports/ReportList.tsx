import type { ReportCase } from '../../api/types';
import { REASON_LABELS } from '../../lib/reports';

/** What the users complained about, one line each. */
export function ReportList({ reports }: { reports: ReportCase['reports'] }) {
  return (
    <section>
      <h3>用戶回報({reports.length})</h3>
      <ul className="report-list">
        {reports.map((r) => (
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
  );
}
