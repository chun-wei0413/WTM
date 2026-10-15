import type { AutomaticEntry } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { useAutomaticUndo } from './useAutomaticUndo';

/** What the rules decided without an administrator, so it can be checked and taken back. */
export function AutomaticList({ entries, onChanged }: { entries: AutomaticEntry[]; onChanged: () => void }) {
  const { busy, error, undo } = useAutomaticUndo(onChanged);

  return (
    <details className="section automatic-list">
      <summary>
        <strong>最近自動處理({entries.length})</strong>
        <span className="muted"> 不需要你決定,但可以檢查、也可以還原(7 天內)</span>
      </summary>
      {error != null && <ErrorNotice error={error} />}
      <ul className="auto-entries">
        {entries.map((e) => (
          <li key={`${e.templateId}-${e.action}`} className="auto-entry">
            <img src={e.imageUrl} alt="" loading="lazy" />
            <div>
              <strong>{e.name}</strong>{' '}
              <span className={e.action === 'APPLIED' ? 'badge badge-success' : 'badge'}>
                {e.action === 'APPLIED' ? '已自動採用 AI 建議' : '已自動忽略(模型認為沒問題)'}
              </span>
              <p className="muted">
                {e.reports} 則回報 · {new Date(e.at).toLocaleString('zh-TW')}
              </p>
              {e.note && <p>模型的說明:{e.note}</p>}
            </div>
            <button type="button" className="button" disabled={!e.canUndo || busy !== null} onClick={() => void undo(e.templateId)}>
              {busy === e.templateId ? '處理中…' : e.action === 'APPLIED' ? '還原成原本的描述' : '重新打開回報'}
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}
