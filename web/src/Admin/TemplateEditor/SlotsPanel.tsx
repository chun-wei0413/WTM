import type { Slot, TemplateStatus } from '../../api/types';
import { SlotForm } from './SlotForm';
import type { Busy } from './useTemplateEditor';

interface Props {
  slots: Slot[];
  selected: number | null;
  selectedSlot: Slot | null;
  retired: boolean;
  busy: Busy;
  status: TemplateStatus;
  version: number;
  /** Whether the slots differ from the saved ones. */
  dirty: boolean;
  /** Why the slots cannot be saved as they are; null when they can. */
  problem: string | null;
  onSelect: (slotNo: number) => void;
  onChange: (slotNo: number, patch: Partial<Slot>) => void;
  onRemove: (slotNo: number) => void;
  onSave: () => void;
}

/** The list of text slots, the form of the selected one, and the button that saves them. */
export function SlotsPanel(props: Props) {
  const { slots, selected, selectedSlot, retired, busy, dirty, problem } = props;
  return (
    <section className="card panel">
      <h2>文字格({slots.length})</h2>
      {slots.length === 0 && <p className="muted">還沒有文字格。</p>}
      <ul className="slot-list">
        {slots.map((s) => (
          <li key={s.slotNo}>
            <button
              type="button"
              className={s.slotNo === selected ? 'slot-item active' : 'slot-item'}
              onClick={() => props.onSelect(s.slotNo)}
            >
              <span className="slot-number">{s.slotNo}</span>
              <span>{s.role || '(未命名)'}</span>
              <span className="muted">≤ {s.maxChars} 字{s.required ? '' : ' · 選填'}</span>
            </button>
          </li>
        ))}
      </ul>

      {selectedSlot && (
        <SlotForm slot={selectedSlot} disabled={retired} onChange={props.onChange} onRemove={props.onRemove} />
      )}

      {props.status === 'APPROVED' && dirty && (
        <div className="notice">
          這個模板已核准:每一項文字格的變更都會產生新版本(目前是 v{props.version})。已經做好的梗圖不受影響。
        </div>
      )}
      {problem && dirty && <p className="field-error">{problem}</p>}
      <button
        type="button"
        className="button button-primary"
        onClick={props.onSave}
        disabled={retired || !dirty || problem !== null || busy !== null}
      >
        {busy === 'slots' ? '儲存中…' : '儲存文字格'}
      </button>
    </section>
  );
}
