import { Link } from 'react-router-dom';
import { ErrorNotice } from '../components/ErrorNotice';
import { useFavorites } from '../favorites/FavoritesContext';

/** Where making a meme starts: the visitor's favorite memes, to pick the picture from. */
export function TemplatePicker() {
  const { items, loading, error } = useFavorites();

  return (
    <div className="page">
      <h1>梗圖模板</h1>
      <p className="lead">從你收藏的梗圖挑一張,加上文字框,做成你自己的梗圖。做好的圖只存在你的瀏覽器,可以下載自己用,不會上傳。</p>

      {loading && <p className="status">載入中…</p>}
      {error != null && <ErrorNotice error={error} />}

      {!loading && error == null && items.length === 0 && (
        <div className="empty">
          <p>你還沒有收藏任何梗圖,先去找一張喜歡的收藏起來。</p>
          <Link to="/" className="button button-primary">
            去找梗圖
          </Link>
        </div>
      )}

      {items.length > 0 && (
        <>
          <h2>選一張收藏的梗圖</h2>
          <div className="grid grid-small">
            {items.map((item) => (
              <Link key={item.templateId} to={`/create?from=${item.templateId}`} className="card template-card picker-card">
                <img src={item.imageUrl} alt={item.meaning ?? item.name} loading="lazy" />
                <div className="card-body">
                  <span className="muted">{item.meaning ?? item.name}</span>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
