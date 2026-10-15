import { ErrorNotice } from '../../components/ErrorNotice';
import type { useNewTemplate } from './useNewTemplate';

/** The fields for a new template: its name and its picture, with a preview. */
export function NewTemplateForm({ draft }: { draft: ReturnType<typeof useNewTemplate> }) {
  const { name, setName, file, preview, creating, createError, onFile, create } = draft;

  return (
    <form onSubmit={create} className="card form new-template">
      <h2>新增模板</h2>
      <label>
        名稱
        <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} />
      </label>
      <label>
        圖片(PNG 或 JPEG,不含文字的原圖最佳)
        <input type="file" accept="image/png,image/jpeg" onChange={onFile} required />
      </label>
      {preview && <img className="upload-preview" src={preview} alt="預覽" />}
      {createError != null && <ErrorNotice error={createError} />}
      <div>
        <button type="submit" className="button button-primary" disabled={creating || !file || !name.trim()}>
          {creating ? '上傳中…' : '建立草稿並開始編輯'}
        </button>
      </div>
    </form>
  );
}
