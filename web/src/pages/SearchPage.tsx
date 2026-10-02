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

  return (
    <div className="page">
      <h1>找梗圖</h1>
      <p className="lead search-lead">描述你遇到的情境,或是這張圖長什麼樣子、大家怎麼叫它,不用記得它的名字。</p>

      <form onSubmit={onSubmit} className="form search-form" role="search">
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
            placeholder="例如:聽不懂對方剛剛在說什麼"
            autoFocus
          />
          <button type="submit" className="button button-primary" disabled={busy || !text.trim()}>
            搜尋
          </button>
        </div>
        <div className="shortcuts">
          <span className="muted">{hot.length > 0 ? '最近熱門搜尋' : '試試看'}</span>
          <div className="chips" aria-label={hot.length > 0 ? '熱門搜尋' : '範例'}>
            {shortcuts.map((term) => (
              <button key={term} type="button" className="chip" onClick={() => pick(term)} disabled={busy}>
                {term}
              </button>
            ))}
          </div>
        </div>
      </form>

      <div className="section-header">
        <h2>{searched === null ? '隨機看看' : `「${searched}」最接近的結果`}</h2>
        <div className="actions">
          {searched !== null && (
            <button type="button" className="button button-quiet" onClick={() => void showRandom()} disabled={busy}>
              清除搜尋
            </button>
          )}
          {searched === null && (
            <button type="button" className="button" onClick={() => void showRandom()} disabled={busy}>
              換一批
            </button>
          )}
        </div>
      </div>

      {load.state === 'loading' && (
        <div className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>載入中…</span>
        </div>
      )}
      {load.state === 'error' && <ErrorNotice error={load.error} />}

      {load.state === 'ready' && load.items.length === 0 && (
        <div className="empty">
          <p>{searched === null ? '圖庫裡還沒有梗圖。' : '沒有找到合適的梗圖,換個說法試試。'}</p>
          {searched === null && <p className="muted">圖片要先收進圖庫、標記完成之後才看得到。</p>}
        </div>
      )}

      {load.state === 'ready' && load.items.length > 0 && (
        <section aria-live="polite">
          <div className="masonry">
            {load.items.map((item) => (
              <MemeTile key={item.templateId} item={item} />
            ))}
          </div>
          {searched !== null && (
            <p className="muted">搜尋一定會列出最接近的幾張,即使圖庫裡沒有真正合適的。</p>
          )}
        </section>
      )}
    </div>
  );
}
