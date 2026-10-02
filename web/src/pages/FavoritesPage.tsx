import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ErrorNotice } from '../components/ErrorNotice';
import { MemeTile } from '../components/MemeTile';
import { useFavorites } from '../favorites/FavoritesContext';

export function FavoritesPage() {
  const { items, loading, error, refresh } = useFavorites();

  // Picture addresses are only good for a while, so ask again each time the page is opened.
  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <div className="page">
      <h1>梗圖收藏</h1>
      <p className="lead">收藏的梗圖在這裡,下次要用就不用再搜尋。也可以拿它加上文字,做成你自己的梗圖。</p>

      {loading && <p className="status">載入中…</p>}
      {error != null && <ErrorNotice error={error} />}

      {!loading && error == null && items.length === 0 && (
        <div className="empty">
          <p>還沒有收藏任何梗圖。</p>
          <Link to="/" className="button button-primary">
            去找梗圖
          </Link>
        </div>
      )}

      {items.length > 0 && (
        <div className="masonry">
          {items.map((item) => (
            <MemeTile key={item.templateId} item={item}>
              <Link to={`/create?from=${item.templateId}`} className="button button-primary">
                加文字
              </Link>
            </MemeTile>
          ))}
        </div>
      )}
    </div>
  );
}
