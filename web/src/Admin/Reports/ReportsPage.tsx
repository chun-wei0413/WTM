import { ErrorNotice } from '../../components/ErrorNotice';
import { AutomaticList } from './AutomaticList';
import { CaseCard } from './CaseCard';
import { useReports } from './useReports';

export function ReportsPage() {
  const { load, refresh } = useReports();

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
      {load.state === 'ready' && load.automatic.length > 0 && (
        <AutomaticList entries={load.automatic} onChanged={() => void refresh()} />
      )}
    </div>
  );
}
