import { useState } from 'react';
import { ErrorNotice } from '../../components/ErrorNotice';
import { IndexSyncNotice } from '../IndexSyncNotice';
import { useIndexSync } from '../useIndexSync';
import { NewTemplateForm } from './NewTemplateForm';
import { TemplateFilters } from './TemplateFilters';
import { TemplateGrid } from './TemplateGrid';
import { useNewTemplate } from './useNewTemplate';
import { useTemplateList } from './useTemplateList';

export function TemplatesPage() {
  const list = useTemplateList();
  const draft = useNewTemplate();
  const { syncing, synced, syncError, sync } = useIndexSync();
  const [adding, setAdding] = useState(false);
  const { load, matching } = list;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>圖庫管理</h1>
          <p className="lead">看每一張圖是什麼、改它的描述與標籤、核准或下架。核准之後,使用者才找得到它。</p>
        </div>
        <div className="actions">
          <button type="button" className="button" onClick={sync} disabled={syncing}>
            {syncing ? '同步中…' : '同步搜尋索引'}
          </button>
          <button type="button" className="button button-primary" onClick={() => setAdding((v) => !v)}>
            {adding ? '取消' : '新增模板'}
          </button>
        </div>
      </div>

      <IndexSyncNotice synced={synced} error={syncError} />

      {adding && <NewTemplateForm draft={draft} />}

      <TemplateFilters
        status={list.status}
        query={list.query}
        counts={load.state === 'ready' ? list.counts : null}
        onStatus={list.choose}
        onQuery={list.search}
      />

      {load.state === 'loading' && <p className="status">載入中…</p>}
      {load.state === 'error' && <ErrorNotice error={load.error} />}
      {load.state === 'ready' && matching.length === 0 && (
        <div className="empty">
          <p>{list.query.trim() ? '沒有符合的圖。' : '這裡還沒有圖。'}</p>
        </div>
      )}
      {load.state === 'ready' && matching.length > 0 && (
        <TemplateGrid matching={matching} shown={list.shown} onShowMore={list.showMore} />
      )}
    </div>
  );
}
