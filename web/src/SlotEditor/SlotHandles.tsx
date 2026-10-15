import type { PointerEvent } from 'react';
import type { Slot } from '../api/types';
import type { Handle } from '../lib/slotGeometry';

const HANDLES: Array<{ handle: Handle; cx: number; cy: number; cursor: string }> = [
  { handle: 'nw', cx: 0, cy: 0, cursor: 'nwse-resize' },
  { handle: 'n', cx: 0.5, cy: 0, cursor: 'ns-resize' },
  { handle: 'ne', cx: 1, cy: 0, cursor: 'nesw-resize' },
  { handle: 'e', cx: 1, cy: 0.5, cursor: 'ew-resize' },
  { handle: 'se', cx: 1, cy: 1, cursor: 'nwse-resize' },
  { handle: 's', cx: 0.5, cy: 1, cursor: 'ns-resize' },
  { handle: 'sw', cx: 0, cy: 1, cursor: 'nesw-resize' },
  { handle: 'w', cx: 0, cy: 0.5, cursor: 'ew-resize' },
];

interface Props {
  slot: Slot;
  /** Side of a handle, in image pixels. */
  size: number;
  onPointerDown: (event: PointerEvent, slot: Slot, handle: Handle) => void;
}

/** The eight handles around a slot, to drag its edges and corners. */
export function SlotHandles({ slot, size, onPointerDown }: Props) {
  return (
    <>
      {HANDLES.map(({ handle, cx, cy, cursor }) => (
        <rect
          key={`${slot.slotNo}-${handle}`}
          className="slot-handle"
          x={slot.x + slot.width * cx - size / 2}
          y={slot.y + slot.height * cy - size / 2}
          width={size}
          height={size}
          style={{ cursor }}
          onPointerDown={(e) => onPointerDown(e, slot, handle)}
        />
      ))}
    </>
  );
}
