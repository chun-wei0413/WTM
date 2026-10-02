/**
 * Geometry for the caption-slot editor. Everything is in image pixels, so the rules here are the
 * same ones the server enforces: a slot must lie inside the image.
 */

export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface Size {
  width: number;
  height: number;
}

export type Handle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';

export const MIN_SLOT_SIZE = 20;

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** Forces a rectangle to whole pixels, at least the minimum size, and fully inside the image. */
export function clampRect(rect: Rect, bounds: Size): Rect {
  const width = clamp(Math.round(rect.width), Math.min(MIN_SLOT_SIZE, bounds.width), bounds.width);
  const height = clamp(Math.round(rect.height), Math.min(MIN_SLOT_SIZE, bounds.height), bounds.height);
  return {
    x: clamp(Math.round(rect.x), 0, bounds.width - width),
    y: clamp(Math.round(rect.y), 0, bounds.height - height),
    width,
    height,
  };
}

/** Moves a rectangle without changing its size, stopping at the image edges. */
export function moveRect(rect: Rect, dx: number, dy: number, bounds: Size): Rect {
  return clampRect({ ...rect, x: rect.x + dx, y: rect.y + dy }, bounds);
}

/** Drags one handle of a rectangle. The opposite edges stay put; the minimum size and image edges hold. */
export function resizeRect(rect: Rect, handle: Handle, dx: number, dy: number, bounds: Size): Rect {
  let left = rect.x;
  let top = rect.y;
  let right = rect.x + rect.width;
  let bottom = rect.y + rect.height;

  if (handle.includes('w')) left = clamp(left + dx, 0, right - MIN_SLOT_SIZE);
  if (handle.includes('e')) right = clamp(right + dx, left + MIN_SLOT_SIZE, bounds.width);
  if (handle.includes('n')) top = clamp(top + dy, 0, bottom - MIN_SLOT_SIZE);
  if (handle.includes('s')) bottom = clamp(bottom + dy, top + MIN_SLOT_SIZE, bounds.height);

  return clampRect({ x: left, y: top, width: right - left, height: bottom - top }, bounds);
}

/** The rectangle spanned by two corner points, whichever way it was dragged. */
export function rectFromPoints(a: { x: number; y: number }, b: { x: number; y: number }, bounds: Size): Rect {
  const x1 = clamp(Math.min(a.x, b.x), 0, bounds.width);
  const y1 = clamp(Math.min(a.y, b.y), 0, bounds.height);
  const x2 = clamp(Math.max(a.x, b.x), 0, bounds.width);
  const y2 = clamp(Math.max(a.y, b.y), 0, bounds.height);
  return { x: Math.round(x1), y: Math.round(y1), width: Math.round(x2 - x1), height: Math.round(y2 - y1) };
}

/** Converts a pointer position on the displayed image into image pixels. */
export function toImageCoords(
  clientX: number,
  clientY: number,
  displayed: { left: number; top: number; width: number; height: number },
  image: Size,
): { x: number; y: number } {
  return {
    x: ((clientX - displayed.left) / displayed.width) * image.width,
    y: ((clientY - displayed.top) / displayed.height) * image.height,
  };
}

export function nextSlotNo(slots: ReadonlyArray<{ slotNo: number }>): number {
  return slots.reduce((highest, s) => Math.max(highest, s.slotNo), 0) + 1;
}

export function isBigEnough(rect: Rect): boolean {
  return rect.width >= MIN_SLOT_SIZE && rect.height >= MIN_SLOT_SIZE;
}
