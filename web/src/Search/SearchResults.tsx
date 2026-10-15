import { ErrorNotice } from '../components/ErrorNotice';
import { MemeTile } from '../components/MemeTile';
import { ResultsHeader } from './ResultsHeader';
import type { Load } from './searchTypes';

interface Props {
  load: Load;
  /** What was searched for; null while the grid shows random memes. */
  searched: string | null;
  busy: boolean;
  onShowRandom: () => void;
}

/** The answer to a search, or random memes before anything was searched. */
export function SearchResults({ load, searched, busy, onShowRandom }: Props) {
  const items = load.state === 'ready' ? load.items : [];
  const [lead, ...rest] = items;

  return (
    <>
      <ResultsHeader
        title={
          searched === null ? (
            '隨機看看'
          ) : (
            <>
              <span className="results-label">最接近「{searched}」的</span>
              {load.state === 'ready' && <span className="results-count">{items.length} 張</span>}
            </>
          )
        }
        actionLabel={searched !== null ? '清除搜尋 ✕' : '換一批 ↻'}
        onAction={onShowRandom}
        disabled={busy}
      />

      {load.state === 'loading' && (
        <div className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>載入中…</span>
        </div>
      )}
      {load.state === 'error' && <ErrorNotice error={load.error} />}

      {load.state === 'ready' && items.length === 0 && (
        <div className="empty">
          <p>{searched === null ? '圖庫裡還沒有梗圖。' : '沒有找到合適的梗圖,換個說法試試。'}</p>
          {searched === null && <p className="muted">圖片要先收進圖庫、標記完成之後才看得到。</p>}
        </div>
      )}

      {lead && (
        <section aria-live="polite">
          {/* keyed, so every new answer slaps down again instead of reusing the old first tile */}
          <MemeTile key={lead.templateId} item={lead} index={1} lead />
          {rest.length > 0 && (
            <div className="masonry masonry-pop">
              {rest.map((item, i) => (
                <MemeTile key={item.templateId} item={item} index={i + 2} />
              ))}
            </div>
          )}
          {searched !== null && <p className="results-note">搜尋一定會列出最接近的幾張,即使圖庫裡沒有真正合適的。</p>}
        </section>
      )}
    </>
  );
}
