import { ErrorNotice } from '../../components/ErrorNotice';
import { useAddUrl } from './useAddUrl';

/** Adds a picture from a web address. */
export function UrlSection() {
  const { url, setUrl, title, setTitle, busy, message, error, valid, submit } = useAddUrl();

  return (
    <section className="section" aria-labelledby="url-title">
      <h2 id="url-title">貼上圖片網址</h2>
      <form onSubmit={submit} className="form url-form">
        <label>
          圖片網址(直接指向圖片檔)
          <input
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://example.com/meme.jpg"
            required
          />
        </label>
        <label>
          名稱(可留空,留空會依圖片內容自動命名)
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} />
        </label>
        <div>
          <button type="submit" className="button button-primary" disabled={busy || !valid}>
            {busy ? '加入中…' : '加入圖庫'}
          </button>
        </div>
      </form>
      {error != null && <ErrorNotice error={error} />}
      {message && (
        <div className={message.kind === 'success' ? 'notice notice-success' : 'notice'} role="status">
          {message.text}
        </div>
      )}
    </section>
  );
}
