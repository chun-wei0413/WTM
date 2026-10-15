import { ErrorNotice } from '../../components/ErrorNotice';
import { UploadSummaryNotice } from './UploadSummaryNotice';
import { useUpload } from './useUpload';

/** Adds pictures from the visitor's computer: some files, or a whole folder. */
export function UploadSection() {
  const { uploading, progress, summary, skipped, error, upload } = useUpload();

  return (
    <section className="section" aria-labelledby="upload-title">
      <h2 id="upload-title">從電腦加入</h2>
      <p className="muted">支援 PNG、JPEG、GIF。內容相同或幾乎一樣的圖會自動略過,不會重複收進來。</p>
      <div className="actions">
        <label className={uploading ? 'button file-button disabled' : 'button file-button'}>
          選擇圖片
          <input type="file" accept="image/png,image/jpeg,image/gif" multiple onChange={upload} disabled={uploading} />
        </label>
        <label className={uploading ? 'button file-button disabled' : 'button file-button'}>
          選擇整個資料夾
          <input
            type="file"
            multiple
            onChange={upload}
            disabled={uploading}
            {...({ webkitdirectory: '' } as Record<string, string>)}
          />
        </label>
      </div>

      {uploading && (
        <p className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>
            上傳中… {progress.done} / {progress.total}
          </span>
        </p>
      )}
      {error != null && <ErrorNotice error={error} />}
      {summary && <UploadSummaryNotice summary={summary} skipped={skipped} />}
    </section>
  );
}
