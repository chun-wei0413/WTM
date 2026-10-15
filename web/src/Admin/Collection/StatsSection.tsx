import { Link } from 'react-router-dom';
import { ErrorNotice } from '../../components/ErrorNotice';
import { IndexSyncNotice } from '../IndexSyncNotice';
import { useIndexSync } from '../useIndexSync';
import { Stat } from './Stat';
import { useLibraryStats } from './useLibraryStats';

/** How many pictures the library has in each state, and the buttons to refresh the numbers or sync the search index. */
export function StatsSection() {
  const { stats, error, pending, refresh } = useLibraryStats();
  const { syncing, synced, syncError, sync } = useIndexSync(refresh);

  return (
    <section className="section" aria-labelledby="stats-title">
      <div className="section-header">
        <h2 id="stats-title">目前的圖庫</h2>
        <div className="actions">
          <button type="button" className="button" onClick={() => void refresh()}>
            重新整理
          </button>
          <button type="button" className="button" onClick={() => void sync()} disabled={syncing}>
            {syncing ? '同步中…' : '同步搜尋索引'}
          </button>
          <Link to="/admin/templates" className="button">
            管理與核准
          </Link>
        </div>
      </div>

      {error != null && <ErrorNotice error={error} />}
      {stats && (
        <dl className="stats">
          <Stat label="圖庫總數" value={stats.total} />
          <Stat label="可被搜尋(已核准)" value={stats.approved} />
          <Stat label="等待標記" value={stats.waitingForTags} />
          <Stat label="標記中" value={stats.beingTagged} />
          <Stat label="標記失敗" value={stats.tagFailures} warn={stats.tagFailures > 0} />
          <Stat label="已下架" value={stats.retired} />
        </dl>
      )}
      {pending > 0 && (
        <p className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>還有 {pending} 張正在等視覺模型標記,這頁會自動更新。</span>
        </p>
      )}
      <IndexSyncNotice synced={synced} error={syncError} />
    </section>
  );
}
