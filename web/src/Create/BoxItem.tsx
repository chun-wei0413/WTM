import { TEXT_STYLES, TEXT_STYLE_IDS, type TextBox, type TextStyleId } from '../lib/memeBoxes';

interface Props {
  box: TextBox;
  /** Position in the list, from 0. */
  index: number;
  selected: boolean;
  fieldRef: (element: HTMLTextAreaElement | null) => void;
  onSelect: () => void;
  onChange: (change: Partial<TextBox>) => void;
  onRemove: () => void;
}

/** The text, the look and the delete button of one text box. */
export function BoxItem({ box, index, selected, fieldRef, onSelect, onChange, onRemove }: Props) {
  return (
    <li className={selected ? 'box-item active' : 'box-item'}>
      <label>
        文字 {index + 1}
        <textarea
          ref={fieldRef}
          value={box.text}
          rows={2}
          maxLength={200}
          onFocus={onSelect}
          onChange={(e) => onChange({ text: e.target.value })}
        />
      </label>
      <div className="box-controls">
        <select
          value={box.style}
          aria-label={`文字 ${index + 1} 的樣式`}
          onChange={(e) => onChange({ style: e.target.value as TextStyleId })}
        >
          {TEXT_STYLE_IDS.map((id) => (
            <option key={id} value={id}>
              {TEXT_STYLES[id].label}
            </option>
          ))}
        </select>
        <button type="button" className="button button-danger" onClick={onRemove}>
          刪除
        </button>
      </div>
    </li>
  );
}
