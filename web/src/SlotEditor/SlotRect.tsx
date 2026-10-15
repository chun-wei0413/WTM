import type { PointerEvent } from 'react';
import type { Slot } from '../api/types';

interface Props {
  slot: Slot;
  selected: boolean;
  /** Whether the slot shows its number; `fontScale` is how many image pixels one screen pixel covers. */
  showLabel: boolean;
  fontScale: number;
  onPointerDown: (event: PointerEvent, slot: Slot) => void;
}

/** One slot on the picture: its outline, and its number. */
export function SlotRect({ slot, selected, showLabel, fontScale, onPointerDown }: Props) {
  return (
    <g>
      <rect
        className={`slot-rect${selected ? ' selected' : ''}${slot.required ? '' : ' optional'}`}
        x={slot.x}
        y={slot.y}
        width={slot.width}
        height={slot.height}
        onPointerDown={(e) => onPointerDown(e, slot)}
      />
      {showLabel && (
        <text
          className="slot-label"
          x={slot.x + slot.width / 2}
          y={slot.y + slot.height / 2}
          fontSize={Math.min(18 * fontScale, slot.height * 0.6)}
          textAnchor="middle"
          dominantBaseline="central"
        >
          {slot.slotNo}
        </text>
      )}
    </g>
  );
}
