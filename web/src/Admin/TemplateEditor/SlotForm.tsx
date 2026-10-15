import type { Slot } from '../../api/types';

interface Props {
  slot: Slot;
  disabled: boolean;
  onChange: (slotNo: number, patch: Partial<Slot>) => void;
  onRemove: (slotNo: number) => void;
}

const number = (value: string) => (value === '' ? 0 : Number(value));

/** The fields of the selected slot: what it is for, how long it may be, and where it sits on the picture. */
export function SlotForm({ slot, disabled, onChange, onRemove }: Props) {
  return (
    <div className="form slot-form">
      <label>
        用途名稱
        <input
          value={slot.role}
          disabled={disabled}
          onChange={(e) => onChange(slot.slotNo, { role: e.target.value })}
          placeholder="例如:被拒絕的事物"
        />
      </label>
      <div className="row">
        <label>
          字數上限
          <input
            type="number"
            min={1}
            value={slot.maxChars}
            disabled={disabled}
            onChange={(e) => onChange(slot.slotNo, { maxChars: number(e.target.value) })}
          />
        </label>
        <label className="check">
          <input
            type="checkbox"
            checked={slot.required}
            disabled={disabled}
            onChange={(e) => onChange(slot.slotNo, { required: e.target.checked })}
          />
          必填
        </label>
      </div>
      <div className="row row-4">
        {(['x', 'y', 'width', 'height'] as const).map((key) => (
          <label key={key}>
            {{ x: 'X', y: 'Y', width: '寬', height: '高' }[key]}
            <input
              type="number"
              value={slot[key]}
              disabled={disabled}
              onChange={(e) => onChange(slot.slotNo, { [key]: number(e.target.value) })}
            />
          </label>
        ))}
      </div>
      <div>
        <button type="button" className="button button-danger" disabled={disabled} onClick={() => onRemove(slot.slotNo)}>
          刪除這個文字格
        </button>
      </div>
    </div>
  );
}
