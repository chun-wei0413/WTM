import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { MemeSummary } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { MemeCard } from '../components/MemeCard';

type Load = { state: 'loading' } | { state: 'error'; error: unknown } | { state: 'ready'; memes: MemeSummary[] };

export function MyMemesPage() {
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  useEffect(() => {
    let cancelled = false;
    api
      .listMemes('KEPT')
      .then((memes) => !cancelled && setLoad({ state: 'ready', memes }))
      .catch((error: unknown) => !cancelled && setLoad({ state: 'error', error }));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="page">
      <h1>我的梗圖</h1>
      <p className="lead">你留下來的梗圖都在這裡,最新的在最前面。</p>

      {load.state === 'loading' && <p className="status">載入中…</p>}
      {load.state === 'error' && <ErrorNotice error={load.error} />}
      {load.state === 'ready' && load.memes.length === 0 && (
        <div className="empty">
          <p>還沒有留下任何梗圖。</p>
          <Link to="/generate" className="button button-primary">
            去產生一張
          </Link>
        </div>
      )}
      {load.state === 'ready' && load.memes.length > 0 && (
        <div className="grid">
          {load.memes.map((m) => (
            <MemeCard
              key={m.id}
              memeId={m.id}
              imageUrl={m.imageUrl}
              templateName={m.templateName}
              captions={m.captions}
              kept
            />
          ))}
        </div>
      )}
    </div>
  );
}
