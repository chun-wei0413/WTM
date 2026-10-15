import type { TextBox } from '../lib/memeBoxes';
import { ErrorNotice } from '../components/ErrorNotice';
import { BoxItem } from './BoxItem';

interface Props {
  boxes: TextBox[];
  selected: number | null;
  saving: boolean;
  /** Why the download failed; null when it did not. */
  error: unknown;
  fieldRef: (id: number) => (element: HTMLTextAreaElement | null) => void;
  onSelect: (id: number) => void;
  onChange: (id: number, change: Partial<TextBox>) => void;
  onRemove: (id: number) => void;
  onAdd: () => void;
  onDownload: () => void;
}

/** The side panel: add a box, download the meme, and edit the text boxes one by one. */
export function BoxPanel(props: Props) {
  const { boxes, selected, saving, error } = props;
  return (
    <aside className="editor-panel" aria-label="文字框設定">
      <div className="actions">
        <button type="button" className="button" onClick={props.onAdd}>
          新增文字框
        </button>
        <button type="button" className="button button-primary" onClick={props.onDownload} disabled={saving}>
          {saving ? '產生中…' : '下載'}
        </button>
      </div>
      {error != null && <ErrorNotice error={error} />}
      {boxes.length === 0 && <p className="muted">還沒有文字框。按「新增文字框」,或直接在圖上拖曳畫一個。</p>}

      <ul className="box-list">
        {boxes.map((box, index) => (
          <BoxItem
            key={box.id}
            box={box}
            index={index}
            selected={box.id === selected}
            fieldRef={props.fieldRef(box.id)}
            onSelect={() => props.onSelect(box.id)}
            onChange={(change) => props.onChange(box.id, change)}
            onRemove={() => props.onRemove(box.id)}
          />
        ))}
      </ul>
    </aside>
  );
}
