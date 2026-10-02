import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react';
import type { Slot } from '../api/types';
import {
  isBigEnough,
  moveRect,
  nextSlotNo,
  rectFromPoints,
  resizeRect,
  toImageCoords,
  type Handle,
  type Rect,
  type Size,
} from '../lib/slotGeometry';

interface Props {
  imageUrl: string;
  imageSize: Size;
  slots: Slot[];
  selected: number | null;
  /** True for a retired template: slots can be looked at but not changed. */
  disabled?: boolean;
  /** Drawn on top of the picture and under the slot outlines, in image pixels (for example a text preview). */
  overlay?: ReactNode;
  /** Whether each slot shows its number. Turn off when the overlay already shows what is in the slot. */
  showLabels?: boolean;
  onSelect: (slotNo: number | null) => void;
  onChange: (slots: Slot[]) => void;
}

type Point = { x: number; y: number };

type Drag =
  | { kind: 'move'; slotNo: number; start: Point; origin: Rect }
  | { kind: 'resize'; slotNo: number; handle: Handle; start: Point; origin: Rect }
  | { kind: 'create'; start: Point };

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

const rectOf = (s: Slot): Rect => ({ x: s.x, y: s.y, width: s.width, height: s.height });

/**
 * Shows the template image with its caption slots drawn on top. Drag on an empty spot to draw a new
 * slot, drag a slot to move it, drag a handle to resize it. All positions are in image pixels, so they
 * are exactly what gets saved.
 */
export function SlotEditor({
  imageUrl,
  imageSize,
  slots,
  selected,
  disabled = false,
  overlay,
  showLabels = true,
  onSelect,
  onChange,
}: Props) {
  const svgRef = useRef<SVGSVGElement>(null);
  const drag = useRef<Drag | null>(null);
  const [preview, setPreview] = useState<Rect | null>(null);
  // How many image pixels one screen pixel covers, so handles and labels keep a readable size.
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const svg = svgRef.current;
    if (!svg) return;
    const update = () => setScale(svg.clientWidth > 0 ? imageSize.width / svg.clientWidth : 1);
    update();
    const observer = new ResizeObserver(update);
    observer.observe(svg);
    return () => observer.disconnect();
  }, [imageSize.width]);

  function pointOf(event: PointerEvent): Point {
    const box = svgRef.current!.getBoundingClientRect();
    return toImageCoords(event.clientX, event.clientY, box, imageSize);
  }

  function begin(event: PointerEvent, next: Drag) {
    drag.current = next;
    svgRef.current?.setPointerCapture(event.pointerId);
  }

  function replace(slotNo: number, rect: Rect) {
    onChange(slots.map((s) => (s.slotNo === slotNo ? { ...s, ...rect } : s)));
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

  function onKeyDown(event: KeyboardEvent) {
    if (disabled || selected == null) return;
    const slot = slots.find((s) => s.slotNo === selected);
    if (!slot) return;
    if (event.key === 'Delete' || event.key === 'Backspace') {
      event.preventDefault();
      onChange(slots.filter((s) => s.slotNo !== selected));
      onSelect(null);
      return;
    }
    const step = event.shiftKey ? 10 : 1;
    const nudge: Record<string, [number, number]> = {
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
    };
    const delta = nudge[event.key];
    if (delta) {
      event.preventDefault();
      replace(slot.slotNo, moveRect(rectOf(slot), delta[0], delta[1], imageSize));
    }
  }

  const handleSize = 12 * scale;

  return (
    <div className="slot-editor" tabIndex={0} onKeyDown={onKeyDown} aria-label="文字格編輯區">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${imageSize.width} ${imageSize.height}`}
        preserveAspectRatio="none"
        style={{ aspectRatio: `${imageSize.width} / ${imageSize.height}`, touchAction: 'none' }}
        onPointerDown={onBackgroundDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerCancel={onUp}
      >
        <image href={imageUrl} width={imageSize.width} height={imageSize.height} preserveAspectRatio="none" />
        {overlay}
        {slots.map((slot) => {
          const isSelected = slot.slotNo === selected;
          return (
            <g key={slot.slotNo}>
              <rect
                className={`slot-rect${isSelected ? ' selected' : ''}${slot.required ? '' : ' optional'}`}
                x={slot.x}
                y={slot.y}
                width={slot.width}
                height={slot.height}
                onPointerDown={(e) => onSlotDown(e, slot)}
              />
              {showLabels && (
                <text
                  className="slot-label"
                  x={slot.x + slot.width / 2}
                  y={slot.y + slot.height / 2}
                  fontSize={Math.min(18 * scale, slot.height * 0.6)}
                  textAnchor="middle"
                  dominantBaseline="central"
                >
                  {slot.slotNo}
                </text>
              )}
            </g>
          );
        })}
        {preview && <rect className="slot-rect preview" {...preview} />}
        {!disabled &&
          slots
            .filter((s) => s.slotNo === selected)
            .flatMap((slot) =>
              HANDLES.map(({ handle, cx, cy, cursor }) => (
                <rect
                  key={`${slot.slotNo}-${handle}`}
                  className="slot-handle"
                  x={slot.x + slot.width * cx - handleSize / 2}
                  y={slot.y + slot.height * cy - handleSize / 2}
                  width={handleSize}
                  height={handleSize}
                  style={{ cursor }}
                  onPointerDown={(e) => onHandleDown(e, slot, handle)}
                />
              )),
            )}
      </svg>
    </div>
  );
}
