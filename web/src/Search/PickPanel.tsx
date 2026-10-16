import { ErrorNotice } from '../components/ErrorNotice';
import { MemeTile } from '../Meme/MemeTile';
import { ResultsHeader } from './ResultsHeader';
import { ShuffleDeck } from './ShuffleDeck';
import type { Picked } from './searchTypes';

/** The answer to "pick one for me": the chosen meme with its reason, then the other candidates. */
export function PickPanel({ picked, onClear }: { picked: Picked; onClear: () => void }) {
  if (picked.state === 'idle') {
    return (
      <div className="pick-hint">
        <p>把處境打在上面的框框裡,按「幫我挑」,等幾秒就有結果。</p>
        <p className="muted">我只會從圖庫現有的梗圖裡挑,不會自己編一張出來。</p>
      </div>
    );
  }

  const result = picked.state === 'ready' ? picked.result : null;
  return (
    <>
      <ResultsHeader
        title={<span className="results-label">{picked.state === 'ready' ? '幫你挑好了' : '挑選中'}</span>}
        actionLabel="清除 ✕"
        onAction={onClear}
        disabled={picked.state === 'loading'}
      />

      {picked.state === 'loading' && (
        <div className="status status-working shuffle-status" role="status">
          <ShuffleDeck />
          <span>正在讀你的處境、從圖庫挑圖…通常要幾秒鐘(很久沒用時,第一次要等久一點)。</span>
        </div>
      )}
      {picked.state === 'error' && <ErrorNotice error={picked.error} />}

      {result && !result.chosen && (
        <div className="empty">
          <p>圖庫裡找不到可以挑的梗圖,換個說法試試。</p>
        </div>
      )}

      {result?.chosen && (
        <section aria-live="polite">
          {result.reason === null && <p className="pick-note">目前無法產生說明,先給你最接近的一張。</p>}
          <MemeTile
            key={result.chosen.templateId}
            item={result.chosen}
            index={1}
            lead
            badge="最接近!"
            reason={result.reason ?? undefined}
          />
          {result.others.length > 0 && (
            <>
              <h3 className="pick-others-title">其他候選</h3>
              <div className="masonry masonry-pop">
                {result.others.map((item, i) => (
                  <MemeTile key={item.templateId} item={item} index={i + 1} />
                ))}
              </div>
            </>
          )}
        </section>
      )}
    </>
  );
}
