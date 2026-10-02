import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../api/client';
import type { SearchResult } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { isHttpUrl, sourceTypeLabel } from '../lib/collection';

type Load =
  | { state: 'idle' }
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; query: string; results: SearchResult[] };

const EXAMPLES = ['聽不懂對方剛剛在說什麼', '黑人問號', '尷尬但不失禮貌地微笑', '終於準時下班了', '被老闆臨時加工作'];

const MAX_LENGTH = 200;

export function SearchPage() {
  const [text, setText] = useState('');
  const [load, setLoad] = useState<Load>({ state: 'idle' });
  // Only the newest search may show its answer; an older, slower one must not overwrite it.
  const latest = useRef(0);

  useEffect(
    () => () => {
      latest.current = -1;
    },
    [],
  );

  async function search(query: string) {
    const q = query.trim();
    if (!q) return;
    const ticket = ++latest.current;
    setLoad({ state: 'loading' });
    try {
      const results = await api.search(q, 12);
      if (latest.current === ticket) setLoad({ state: 'ready', query: q, results });
    } catch (error) {
      if (latest.current === ticket) setLoad({ state: 'error', error });
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    void search(text);
  }

  function pickExample(example: string) {
    setText(example);
    void search(example);
  }

  const busy = load.state === 'loading';

  return (
    <div className="page">
      <h1>找梗圖</h1>
      <p className="lead">
        描述你遇到的情境,或是這張圖長什麼樣子、大家怎麼叫它,不用記得它的名字。
      </p>

      <form onSubmit={onSubmit} className="form search-form" role="search">
        <label>
          你想找的梗圖
          <div className="search-row">
            <input
              type="search"
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={MAX_LENGTH}
              placeholder="例如:聽不懂對方剛剛在說什麼"
              autoFocus
            />
            <button type="submit" className="button button-primary" disabled={busy || !text.trim()}>
              {busy ? '搜尋中…' : '搜尋'}
            </button>
          </div>
        </label>
        <div className="chips" aria-label="範例">
          {EXAMPLES.map((example) => (
            <button key={example} type="button" className="chip" onClick={() => pickExample(example)} disabled={busy}>
              {example}
            </button>
          ))}
        </div>
      </form>

      {load.state === 'loading' && (
        <div className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>搜尋中…</span>
        </div>
      )}
      {load.state === 'error' && <ErrorNotice error={load.error} />}

      {load.state === 'ready' && load.results.length === 0 && (
        <div className="empty">
          <p>圖庫裡還沒有可以搜尋的梗圖。</p>
          <p className="muted">圖片要先收進圖庫、標記完成並核准之後才找得到。</p>
        </div>
      )}

      {load.state === 'ready' && load.results.length > 0 && (
        <section aria-live="polite">
          <h2>「{load.query}」最接近的 {load.results.length} 張</h2>
          <div className="grid">
            {load.results.map((r) => (
              <ResultCard key={r.templateId} result={r} />
            ))}
          </div>
          <p className="muted">搜尋一定會列出最接近的幾張,即使圖庫裡沒有真正合適的。</p>
        </section>
      )}
    </div>
  );
}

function ResultCard({ result }: { result: SearchResult }) {
  const source = sourceTypeLabel(result.sourceType);
  return (
    <article className="card result-card">
      <a href={result.imageUrl} target="_blank" rel="noreferrer" className="result-image" title="開啟原圖">
        <img src={result.imageUrl} alt={result.meaning ?? result.name} loading="lazy" />
      </a>
      <div className="card-body">
        <div className="card-title">
          <span>{result.name}</span>
        </div>
        {result.meaning && <p className="result-meaning">{result.meaning}</p>}
        {result.imageText && <p className="muted">圖中文字:{result.imageText}</p>}
        {result.tags.length > 0 && (
          <ul className="tag-list" aria-label="標籤">
            {result.tags.map((tag) => (
              <li key={tag}>{tag}</li>
            ))}
          </ul>
        )}
        {(source || result.attribution) && (
          <span className="muted">
            來源:
            {result.sourceUrl && isHttpUrl(result.sourceUrl) ? (
              <a href={result.sourceUrl} target="_blank" rel="noreferrer">
                {source ?? '原始網址'}
              </a>
            ) : (
              source
            )}
            {result.attribution && `(${result.attribution})`}
          </span>
        )}
      </div>
    </article>
  );
}
