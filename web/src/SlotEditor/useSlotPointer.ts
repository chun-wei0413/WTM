import { useRef, useState, type PointerEvent, type RefObject } from 'react';
import type { Slot } from '../api/types';
import {
  isBigEnough,
  moveRect,
  nextSlotNo,
  rectFromPoints,
  rectOf,
  resizeRect,
  toImageCoords,
  withRect,
  type Handle,
  type Rect,
  type Size,
} from '../lib/slotGeometry';

type Point = { x: number; y: number };

type Drag =
  | { kind: 'move'; slotNo: number; start: Point; origin: Rect }
  | { kind: 'resize'; slotNo: number; handle: Handle; start: Point; origin: Rect }
  | { kind: 'create'; start: Point };

interface Options {
  svgRef: RefObject<SVGSVGElement | null>;
  imageSize: Size;
  slots: Slot[];
  disabled: boolean;
  onSelect: (slotNo: number | null) => void;
  onChange: (slots: Slot[]) => void;
}

/**
 * What the pointer does on the picture: drag on an empty spot to draw a new slot, drag a slot to move it,
 * drag a handle to resize it. `preview` is the outline of the slot being drawn.
 */
export function useSlotPointer({ svgRef, imageSize, slots, disabled, onSelect, onChange }: Options) {
  const drag = useRef<Drag | null>(null);
  const [preview, setPreview] = useState<Rect | null>(null);

  function pointOf(event: PointerEvent): Point {
    const box = svgRef.current!.getBoundingClientRect();
    return toImageCoords(event.clientX, event.clientY, box, imageSize);
  }

  function begin(event: PointerEvent, next: Drag) {
    drag.current = next;
    svgRef.current?.setPointerCapture(event.pointerId);
  }

  function replace(slotNo: number, rect: Rect) {
    onChange(withRect(slots, slotNo, rect));
  }

  function onBackgroundDown(event: PointerEvent) {
    if (disabled) return;
    onSelect(null);
    begin(event, { kind: 'create', start: pointOf(event) });
  }

  function onSlotDown(event: PointerEvent, slot: Slot) {
    event.stopPropagation();
    onSelect(slot.slotNo);
    if (disabled) return;
    begin(event, { kind: 'move', slotNo: slot.slotNo, start: pointOf(event), origin: rectOf(slot) });
  }

  function onHandleDown(event: PointerEvent, slot: Slot, handle: Handle) {
    event.stopPropagation();
    if (disabled) return;
    begin(event, { kind: 'resize', slotNo: slot.slotNo, handle, start: pointOf(event), origin: rectOf(slot) });
  }

  function onMove(event: PointerEvent) {
    const active = drag.current;
    if (!active) return;
    const here = pointOf(event);
    if (active.kind === 'create') {
      setPreview(rectFromPoints(active.start, here, imageSize));
    } else {
      const dx = here.x - active.start.x;
      const dy = here.y - active.start.y;
      replace(
        active.slotNo,
        active.kind === 'move'
          ? moveRect(active.origin, dx, dy, imageSize)
          : resizeRect(active.origin, active.handle, dx, dy, imageSize),
      );
    }
  }

  function onUp(event: PointerEvent) {
    const active = drag.current;
    drag.current = null;
    if (svgRef.current?.hasPointerCapture(event.pointerId)) svgRef.current.releasePointerCapture(event.pointerId);
    if (active?.kind !== 'create') return;
    const rect = rectFromPoints(active.start, pointOf(event), imageSize);
    setPreview(null);
    if (!isBigEnough(rect)) return; // a plain click, or a drag too small to be a slot
    const slotNo = nextSlotNo(slots);
    onChange([
      ...slots,
      { slotNo, role: `文字格 ${slotNo}`, maxChars: Math.max(4, Math.round(rect.width / 24)), required: true, ...rect },
    ]);
    onSelect(slotNo);
  }

  return { preview, onBackgroundDown, onSlotDown, onHandleDown, onMove, onUp };
}
