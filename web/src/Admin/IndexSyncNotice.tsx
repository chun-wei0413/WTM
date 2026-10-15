import type { IndexSyncResult } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';

/** What a search index sync did, or why it failed; nothing while there is neither. */
export function IndexSyncNotice({ synced, error }: { synced: IndexSyncResult | null; error: unknown }) {
  return (
    <>
      {synced && (
        <div className="notice notice-success" role="status">
          搜尋索引已同步:建立或更新 {synced.indexed} 個、移除 {synced.removed} 個
          {synced.failed > 0 && `,有 ${synced.failed} 個失敗(稍後會自動重試)`}。
        </div>
      )}
      {error != null && <ErrorNotice error={error} />}
    </>
  );
}
