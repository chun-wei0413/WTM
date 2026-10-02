import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { Slot } from '../api/types';
import { ErrorNotice } from '../components/ErrorNotice';
import { SlotEditor } from '../components/SlotEditor';
import { useFavorites } from '../favorites/FavoritesContext';
import { saveBlob } from '../lib/download';
import {
  FONT_FAMILY,
  OUTLINE_RATIO,
  TEXT_STYLES,
  TEXT_STYLE_IDS,
  bottomBox,
  nextBoxId,
  placeText,
  type TextBox,
  type TextStyleId,
} from '../lib/memeBoxes';
import { canvasMeasure, renderMeme } from '../lib/renderMeme';
import type { Size } from '../lib/slotGeometry';

type Picture =
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; url: string; image: HTMLImageElement; size: Size; type: string };

/** Loads a picture through the application and decodes it, so its size is known and a canvas may draw it. */
function usePicture(templateId: string | null): Picture {
  const [picture, setPicture] = useState<Picture>({ state: 'loading' });

  useEffect(() => {
    if (!templateId) return;
    let cancelled = false;
    let url: string | null = null;
    setPicture({ state: 'loading' });
    (async () => {
      const blob = await api.library.image(templateId);
      url = URL.createObjectURL(blob);
      const image = new Image();
      image.src = url;
      await image.decode();
      if (!cancelled) {
        setPicture({
          state: 'ready',
          url,
          image,
          size: { width: image.naturalWidth, height: image.naturalHeight },
          type: blob.type,
        });
      }
    })().catch((error: unknown) => !cancelled && setPicture({ state: 'error', error }));
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [templateId]);

  return picture;
}

export function EditorPage() {
  const [params] = useSearchParams();
  const from = params.get('from');
  return from ? <Editor key={from} templateId={from} /> : <Picker />;
}

/* --------------------------------------------------------------- picker */

function Picker() {
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
              <Link key={item.templateId} to={`/create?from=${item.templateId}`} className="card template-card">
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

/* --------------------------------------------------------------- editor */

function Editor({ templateId }: { templateId: string }) {
  const picture = usePicture(templateId);
  const [boxes, setBoxes] = useState<TextBox[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const fields = useRef(new Map<number, HTMLTextAreaElement>());
  const previousCount = useRef(0);

  const size = picture.state === 'ready' ? picture.size : null;

  // Put the cursor in the text field of a box the moment it appears, so typing can start at once.
  useEffect(() => {
    if (boxes.length > previousCount.current) {
      const newest = boxes[boxes.length - 1];
      if (newest) fields.current.get(newest.id)?.focus();
    }
    previousCount.current = boxes.length;
  }, [boxes]);

  function addBox() {
    if (!size) return;
    const box = bottomBox(nextBoxId(boxes), size);
    setBoxes((current) => [...current, box]);
    setSelected(box.id);
  }

  function update(id: number, change: Partial<TextBox>) {
    setBoxes((current) => current.map((b) => (b.id === id ? { ...b, ...change } : b)));
  }

  function remove(id: number) {
    setBoxes((current) => current.filter((b) => b.id !== id));
    setSelected((current) => (current === id ? null : current));
  }

  /** The slot editor speaks in slots; a text box is a slot with some text and a look attached. */
  const slots: Slot[] = boxes.map((b) => ({
    slotNo: b.id,
    role: '',
    maxChars: 0,
    required: true,
    x: b.x,
    y: b.y,
    width: b.width,
    height: b.height,
  }));

  function onSlotsChange(next: Slot[]) {
    if (!size) return;
    setBoxes((current) =>
      next.map((slot) => {
        const rect = { x: slot.x, y: slot.y, width: slot.width, height: slot.height };
        const existing = current.find((b) => b.id === slot.slotNo);
        return existing ? { ...existing, ...rect } : { ...bottomBox(slot.slotNo, size), ...rect, text: '輸入文字' };
      }),
    );
  }

  const measure = useMemo(() => canvasMeasure(), []);

  async function download() {
    if (picture.state !== 'ready') return;
    setSaving(true);
    setError(null);
    try {
      const blob = await renderMeme(picture.image, picture.size, boxes);
      saveBlob(blob, `my-meme-${templateId.slice(0, 8)}.png`);
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

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
          <div>
            <SlotEditor
              imageUrl={picture.url}
              imageSize={picture.size}
              slots={slots}
              selected={selected}
              showLabels={false}
              overlay={boxes.map((box) => (
                <TextPreview key={box.id} box={box} measure={measure} />
              ))}
              onSelect={setSelected}
              onChange={onSlotsChange}
            />
            {picture.type === 'image/gif' && <p className="muted">這是 GIF 動圖,加上文字後會存成靜態圖片。</p>}
          </div>

          <aside className="editor-panel" aria-label="文字框設定">
            <div className="actions">
              <button type="button" className="button" onClick={addBox}>
                新增文字框
              </button>
              <button type="button" className="button button-primary" onClick={download} disabled={saving}>
                {saving ? '產生中…' : '下載'}
              </button>
            </div>
            {error != null && <ErrorNotice error={error} />}
            {boxes.length === 0 && <p className="muted">還沒有文字框。按「新增文字框」,或直接在圖上拖曳畫一個。</p>}

            <ul className="box-list">
              {boxes.map((box, index) => (
                <li key={box.id} className={box.id === selected ? 'box-item active' : 'box-item'}>
                  <label>
                    文字 {index + 1}
                    <textarea
                      ref={(element) => {
                        if (element) fields.current.set(box.id, element);
                        else fields.current.delete(box.id);
                      }}
                      value={box.text}
                      rows={2}
                      maxLength={200}
                      onFocus={() => setSelected(box.id)}
                      onChange={(e) => update(box.id, { text: e.target.value })}
                    />
                  </label>
                  <div className="box-controls">
                    <select
                      value={box.style}
                      aria-label={`文字 ${index + 1} 的樣式`}
                      onChange={(e) => update(box.id, { style: e.target.value as TextStyleId })}
                    >
                      {TEXT_STYLE_IDS.map((id) => (
                        <option key={id} value={id}>
                          {TEXT_STYLES[id].label}
                        </option>
                      ))}
                    </select>
                    <button type="button" className="button button-danger" onClick={() => remove(box.id)}>
                      刪除
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </aside>
        </div>
      )}
    </div>
  );
}

/** The text of one box as it will be exported, drawn inside the editor's SVG. */
function TextPreview({ box, measure }: { box: TextBox; measure: (text: string, size: number) => number }) {
  const placed = placeText(box, measure);
  if (!placed) return null;
  const style = TEXT_STYLES[box.style];
  return (
    <g
      pointerEvents="none"
      fontFamily={FONT_FAMILY}
      fontWeight={800}
      fontSize={placed.fontSize}
      textAnchor="middle"
      dominantBaseline="central"
      fill={style.fill}
      stroke={style.outline}
      strokeWidth={placed.fontSize * OUTLINE_RATIO}
      strokeLinejoin="round"
      style={{ paintOrder: 'stroke fill' }}
    >
      {placed.lines.map((line, i) => (
        <text key={i} x={line.x} y={line.y}>
          {line.text}
        </text>
      ))}
    </g>
  );
}
