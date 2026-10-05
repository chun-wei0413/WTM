import { useCallback, useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { api } from '../api/client';
import type { LibraryItem, PickResult } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { MemeTile } from '../components/MemeTile';

type Load =
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; items: LibraryItem[] };

type Mode = 'search' | 'pick';

type Picked =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; result: PickResult };

/** Shown when nobody has searched for anything yet. */
const EXAMPLES = ['聽不懂對方剛剛在說什麼', '黑人問號', '尷尬但不失禮貌地微笑', '終於準時下班了', '被老闆臨時加工作'];

/** Whole situations, for the "pick one for me" mode. */
const PICK_EXAMPLES = [
  '朋友一直說我 over react,幫我挑一張回他的梗圖',
  '同事每隔十分鐘就問我進度,我快崩潰了',
  '把拖了三個月的報告終於交出去了',
];

const MAX_LENGTH = 200;
const PICK_MAX_LENGTH = 300;
const RANDOM_COUNT = 12;

export function SearchPage() {
  const [mode, setMode] = useState<Mode>('search');
  const [text, setText] = useState('');
  const [situation, setSituation] = useState('');
  /** What the grid below shows: the answer to this search, or random memes when it is null. */
  const [searched, setSearched] = useState<string | null>(null);
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [picked, setPicked] = useState<Picked>({ state: 'idle' });
  const [hot, setHot] = useState<string[]>([]);
  // Only the newest request may show its answer; an older, slower one must not overwrite it.
  const latest = useRef(0);
  const latestPick = useRef(0);

  const showRandom = useCallback(async () => {
    const ticket = ++latest.current;
    setSearched(null);
    setLoad({ state: 'loading' });
    try {
      const items = await api.library.random(RANDOM_COUNT);
      if (latest.current === ticket) setLoad({ state: 'ready', items });
    } catch (error) {
      if (latest.current === ticket) setLoad({ state: 'error', error });
    }
  }, []);

  const search = useCallback(async (query: string) => {
    const q = query.trim();
    if (!q) return;
    const ticket = ++latest.current;
    setSearched(q);
    setLoad({ state: 'loading' });
    try {
      const items = await api.search(q, 12);
      if (latest.current === ticket) setLoad({ state: 'ready', items });
    } catch (error) {
      if (latest.current === ticket) setLoad({ state: 'error', error });
    }
  }, []);

  const pickFor = useCallback(async (described: string) => {
    const s = described.trim();
    if (!s) return;
    const ticket = ++latestPick.current;
    setPicked({ state: 'loading' });
    try {
      const result = await api.pick(s);
      if (latestPick.current === ticket) setPicked({ state: 'ready', result });
    } catch (error) {
      if (latestPick.current === ticket) setPicked({ state: 'error', error });
    }
  }, []);

  useEffect(() => {
    void showRandom();
    api.library
      .hotSearches(8)
      .then((terms) => setHot(terms.map((t) => t.term)))
      .catch(() => undefined); // the shortcuts are a convenience; the page works without them
    return () => {
      latest.current = -1;
      latestPick.current = -1;
    };
  }, [showRandom]);

  const picking = mode === 'pick';
  const busy = picking ? picked.state === 'loading' : load.state === 'loading';

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    void (picking ? pickFor(situation) : search(text));
  }

  /** Enter sends the situation, Shift+Enter starts a new line; Enter that confirms a Chinese word does neither. */
  function onSituationKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (!busy) void pickFor(situation);
    }
  }

  function chooseShortcut(term: string) {
    if (picking) {
      setSituation(term);
      void pickFor(term);
    } else {
      setText(term);
      void search(term);
    }
  }

  const shortcuts = picking ? PICK_EXAMPLES : hot.length > 0 ? hot : EXAMPLES;
  const shortcutsLabel = picking ? '試試看' : hot.length > 0 ? '最近熱門' : '試試看';

  const items = load.state === 'ready' ? load.items : [];
  const [lead, ...rest] = items;

  return (
    <div className="page search-page">
      <header className="masthead">
        <p className="eyebrow">WTM · 梗圖檢索</p>
        <h1>找梗圖</h1>
        <p className="lead search-lead">
          {picking
            ? '把你遇到的處境講出來,例如對方說了什麼、你想表達什麼。我會從圖庫找出最接近的一張,並說說它適不適合。'
            : '描述你遇到的情境,或是這張圖長什麼樣子、大家怎麼叫它,不用記得它的名字。'}
        </p>
      </header>

      <form onSubmit={onSubmit} className="search-form" role="search">
        <div className="mode-switch" role="tablist" aria-label="找梗圖的方式">
          <button
            type="button"
            role="tab"
            aria-selected={!picking}
            className="mode-tab"
            onClick={() => setMode('search')}
          >
            搜尋
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={picking}
            className="mode-tab"
            onClick={() => setMode('pick')}
          >
            幫我挑一張
          </button>
        </div>

        {picking ? (
          <>
            <label className="visually-hidden" htmlFor="situation-box">
              你遇到的處境
            </label>
            <div className="search-row search-row-tall">
              <textarea
                id="situation-box"
                value={situation}
                onChange={(e) => setSituation(e.target.value)}
                onKeyDown={onSituationKeyDown}
                maxLength={PICK_MAX_LENGTH}
                rows={2}
                placeholder="朋友一直說我 over react,幫我挑一張回他的梗圖"
                autoFocus
              />
              <button type="submit" className="search-submit" disabled={busy || !situation.trim()}>
                幫我挑 <span aria-hidden="true">→</span>
              </button>
            </div>
          </>
        ) : (
          <>
            <label className="visually-hidden" htmlFor="search-box">
              你想找的梗圖
            </label>
            <div className="search-row">
              <input
                id="search-box"
                type="search"
                value={text}
                onChange={(e) => setText(e.target.value)}
                maxLength={MAX_LENGTH}
                placeholder="聽不懂對方剛剛在說什麼"
                autoFocus
              />
              <button type="submit" className="search-submit" disabled={busy || !text.trim()}>
                搜尋 <span aria-hidden="true">→</span>
              </button>
            </div>
          </>
        )}

        <div className="shortcuts">
          <span className="shortcuts-label">{shortcutsLabel}</span>
          <ul className="shortcut-list" aria-label={picking ? '範例處境' : hot.length > 0 ? '熱門搜尋' : '範例'}>
            {shortcuts.map((term) => (
              <li key={term}>
                <button type="button" className="shortcut" onClick={() => chooseShortcut(term)} disabled={busy}>
                  {term}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </form>

      {picking ? (
        <PickPanel picked={picked} onClear={() => setPicked({ state: 'idle' })} />
      ) : (
        <>
          <div className="section-header results-head">
            <h2>
              {searched === null ? (
                '隨機看看'
              ) : (
                <>
                  <span className="results-label">最接近「{searched}」的</span>
                  {load.state === 'ready' && <span className="results-count">{items.length} 張</span>}
                </>
              )}
            </h2>
            <div className="actions">
              <button type="button" className="text-action" onClick={() => void showRandom()} disabled={busy}>
                {searched !== null ? '清除搜尋 ✕' : '換一批 ↻'}
              </button>
            </div>
          </div>

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
              <MemeTile item={lead} index={1} lead />
              {rest.length > 0 && (
                <div className="masonry masonry-pop">
                  {rest.map((item, i) => (
                    <MemeTile key={item.templateId} item={item} index={i + 2} />
                  ))}
                </div>
              )}
              {searched !== null && (
                <p className="results-note">搜尋一定會列出最接近的幾張,即使圖庫裡沒有真正合適的。</p>
              )}
            </section>
          )}
        </>
      )}
    </div>
  );
}

/** The answer to "pick one for me": the chosen meme with its reason, then the other candidates. */
function PickPanel({ picked, onClear }: { picked: Picked; onClear: () => void }) {
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
      <div className="section-header results-head">
        <h2>
          <span className="results-label">{picked.state === 'ready' ? '幫你挑好了' : '挑選中'}</span>
        </h2>
        <div className="actions">
          <button type="button" className="text-action" onClick={onClear} disabled={picked.state === 'loading'}>
            清除 ✕
          </button>
        </div>
      </div>

      {picked.state === 'loading' && (
        <div className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
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
          {result.reason === null && (
            <p className="pick-note">目前無法產生說明,先給你最接近的一張。</p>
          )}
          <MemeTile
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
