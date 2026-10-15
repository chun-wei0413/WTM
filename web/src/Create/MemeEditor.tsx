import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ErrorNotice } from '../components/ErrorNotice';
import { canvasMeasure } from '../lib/renderMeme';
import { SlotEditor } from '../SlotEditor/SlotEditor';
import { BoxPanel } from './BoxPanel';
import { TextPreview } from './TextPreview';
import { useMemeBoxes } from './useMemeBoxes';
import { useMemeDownload } from './useMemeDownload';
import { usePicture } from './usePicture';

/** Puts text boxes on one picture and downloads the result. */
export function MemeEditor({ templateId }: { templateId: string }) {
  const picture = usePicture(templateId);
  const size = picture.state === 'ready' ? picture.size : null;
  const meme = useMemeBoxes(size);
  const { saving, error, download } = useMemeDownload(picture, meme.boxes, templateId);
  const measure = useMemo(() => canvasMeasure(), []);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>梗圖模板</h1>
          <p className="lead">
            在圖上拖曳可以畫出文字框,拖動文字框可以移動,拖動邊角可以調整大小;文字會自動縮放到剛好放進框裡。
          </p>
        </div>
        <Link to="/create" className="button">
          換一張
        </Link>
      </div>

      {picture.state === 'loading' && (
        <div className="status status-working" role="status">
          <span className="spinner" aria-hidden="true" />
          <span>載入圖片中…</span>
        </div>
      )}
      {picture.state === 'error' && <ErrorNotice error={picture.error} />}

      {picture.state === 'ready' && (
        <div className="editor-layout">
          <div className="editor-stage" style={{ ['--ratio' as string]: picture.size.width / picture.size.height }}>
            <SlotEditor
              imageUrl={picture.url}
              imageSize={picture.size}
              slots={meme.slots}
              selected={meme.selected}
              showLabels={false}
              overlay={meme.boxes.map((box) => (
                <TextPreview key={box.id} box={box} measure={measure} />
              ))}
              onSelect={meme.setSelected}
              onChange={meme.onSlotsChange}
            />
            {picture.type === 'image/gif' && <p className="muted">這是 GIF 動圖,加上文字後會存成靜態圖片。</p>}
          </div>

          <BoxPanel
            boxes={meme.boxes}
            selected={meme.selected}
            saving={saving}
            error={error}
            fieldRef={meme.fieldRef}
            onSelect={meme.setSelected}
            onChange={meme.updateBox}
            onRemove={meme.removeBox}
            onAdd={meme.addBox}
            onDownload={download}
          />
        </div>
      )}
    </div>
  );
}
