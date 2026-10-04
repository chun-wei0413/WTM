import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { LibraryItem } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { MemeTile } from '../components/MemeTile';

type Load =
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; items: LibraryItem[] };

/** Shown when nobody has searched for anything yet. */
const EXAMPLES = ['聽不懂對方剛剛在說什麼', '黑人問號', '尷尬但不失禮貌地微笑', '終於準時下班了', '被老闆臨時加工作'];

const MAX_LENGTH = 200;
const RANDOM_COUNT = 12;

export function SearchPage() {
  const [text, setText] = useState('');
  /** What the grid below shows: the answer to this search, or random memes when it is null. */
  const [searched, setSearched] = useState<string | null>(null);
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [hot, setHot] = useState<string[]>([]);
  // Only the newest request may show its answer; an older, slower one must not overwrite it.
  const latest = useRef(0);

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

  useEffect(() => {
    void showRandom();
    api.library
      .hotSearches(8)
      .then((terms) => setHot(terms.map((t) => t.term)))
      .catch(() => undefined); // the shortcuts are a convenience; the page works without them
    return () => {
      latest.current = -1;
    };
  }, [showRandom]);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void search(text);
  }

  function pick(term: string) {
    setText(term);
    void search(term);
  }

  const busy = load.state === 'loading';
  const shortcuts = hot.length > 0 ? hot : EXAMPLES;

  const items = load.state === 'ready' ? load.items : [];
  const [lead, ...rest] = items;

  return (
    <div className="page search-page">
      <header className="masthead">
        <p className="eyebrow">WTM · 梗圖檢索</p>
        <h1>找梗圖</h1>
        <p className="lead search-lead">描述你遇到的情境,或是這張圖長什麼樣子、大家怎麼叫它,不用記得它的名字。</p>
      </header>

      <form onSubmit={onSubmit} className="search-form" role="search">
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
        <div className="shortcuts">
          <span className="shortcuts-label">{hot.length > 0 ? '最近熱門' : '試試看'}</span>
          <ul className="shortcut-list" aria-label={hot.length > 0 ? '熱門搜尋' : '範例'}>
            {shortcuts.map((term) => (
              <li key={term}>
                <button type="button" className="shortcut" onClick={() => pick(term)} disabled={busy}>
                  {term}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </form>

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
    </div>
  );
}
