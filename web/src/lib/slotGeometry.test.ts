import { describe, expect, it } from 'vitest';
import {
  clampRect,
  isBigEnough,
  MIN_SLOT_SIZE,
  moveRect,
  nextSlotNo,
  rectFromPoints,
  resizeRect,
  toImageCoords,
  type Rect,
} from './slotGeometry';

const IMAGE = { width: 600, height: 400 };
const RECT: Rect = { x: 100, y: 100, width: 200, height: 100 };

describe('clampRect', () => {
  it('leaves a valid rectangle alone', () => {
    expect(clampRect(RECT, IMAGE)).toEqual(RECT);
  });

  it('rounds to whole pixels', () => {
    expect(clampRect({ x: 10.4, y: 20.6, width: 50.5, height: 30.2 }, IMAGE)).toEqual({ x: 10, y: 21, width: 51, height: 30 });
  });

  it('pulls a rectangle that sticks out back inside', () => {
    expect(clampRect({ x: 500, y: 350, width: 200, height: 100 }, IMAGE)).toEqual({ x: 400, y: 300, width: 200, height: 100 });
    expect(clampRect({ x: -30, y: -5, width: 100, height: 100 }, IMAGE)).toEqual({ x: 0, y: 0, width: 100, height: 100 });
  });

  it('never makes a rectangle smaller than the minimum or larger than the image', () => {
    expect(clampRect({ x: 0, y: 0, width: 3, height: 1 }, IMAGE)).toMatchObject({ width: MIN_SLOT_SIZE, height: MIN_SLOT_SIZE });
    expect(clampRect({ x: 0, y: 0, width: 9999, height: 9999 }, IMAGE)).toEqual({ x: 0, y: 0, width: 600, height: 400 });
  });
});

describe('moveRect', () => {
  it('shifts the rectangle and keeps its size', () => {
    expect(moveRect(RECT, 25, -10, IMAGE)).toEqual({ x: 125, y: 90, width: 200, height: 100 });
  });

  it('stops at every edge of the image', () => {
    expect(moveRect(RECT, -999, -999, IMAGE)).toMatchObject({ x: 0, y: 0 });
    expect(moveRect(RECT, 999, 999, IMAGE)).toMatchObject({ x: 400, y: 300 });
  });
});

describe('resizeRect', () => {
  it('moves only the edges the handle owns', () => {
    expect(resizeRect(RECT, 'e', 50, 999, IMAGE)).toEqual({ x: 100, y: 100, width: 250, height: 100 });
    expect(resizeRect(RECT, 's', 999, 40, IMAGE)).toEqual({ x: 100, y: 100, width: 200, height: 140 });
    expect(resizeRect(RECT, 'w', -40, 999, IMAGE)).toEqual({ x: 60, y: 100, width: 240, height: 100 });
    expect(resizeRect(RECT, 'n', 999, -30, IMAGE)).toEqual({ x: 100, y: 70, width: 200, height: 130 });
  });

  it('a corner handle moves two edges and leaves the opposite corner fixed', () => {
    const resized = resizeRect(RECT, 'nw', -20, -20, IMAGE);

    expect(resized).toEqual({ x: 80, y: 80, width: 220, height: 120 });
    expect(resized.x + resized.width).toBe(300);
    expect(resized.y + resized.height).toBe(200);
  });

  it('cannot shrink below the minimum size, and the far edge stays where it was', () => {
    const shrunk = resizeRect(RECT, 'w', 9999, 0, IMAGE);

    expect(shrunk.width).toBe(MIN_SLOT_SIZE);
    expect(shrunk.x + shrunk.width).toBe(300);
  });

  it('cannot be dragged outside the image', () => {
    expect(resizeRect(RECT, 'se', 9999, 9999, IMAGE)).toEqual({ x: 100, y: 100, width: 500, height: 300 });
    expect(resizeRect(RECT, 'nw', -9999, -9999, IMAGE)).toEqual({ x: 0, y: 0, width: 300, height: 200 });
  });

  it('the result always passes the same rules the server checks', () => {
    const handles = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const;
    for (const handle of handles) {
      for (const [dx, dy] of [[-700, -700], [700, 700], [-30, 45], [13, -77]] as const) {
        const r = resizeRect(RECT, handle, dx, dy, IMAGE);
        expect(r.x).toBeGreaterThanOrEqual(0);
        expect(r.y).toBeGreaterThanOrEqual(0);
        expect(r.x + r.width).toBeLessThanOrEqual(IMAGE.width);
        expect(r.y + r.height).toBeLessThanOrEqual(IMAGE.height);
        expect(isBigEnough(r)).toBe(true);
      }
    }
  });
});

describe('rectFromPoints', () => {
  it('works whichever corner the drag started from', () => {
    const expected = { x: 50, y: 60, width: 100, height: 40 };

    expect(rectFromPoints({ x: 50, y: 60 }, { x: 150, y: 100 }, IMAGE)).toEqual(expected);
    expect(rectFromPoints({ x: 150, y: 100 }, { x: 50, y: 60 }, IMAGE)).toEqual(expected);
    expect(rectFromPoints({ x: 150, y: 60 }, { x: 50, y: 100 }, IMAGE)).toEqual(expected);
  });

  it('is cut off at the image edges', () => {
    expect(rectFromPoints({ x: -50, y: -50 }, { x: 9999, y: 9999 }, IMAGE)).toEqual({ x: 0, y: 0, width: 600, height: 400 });
  });

  it('a click without dragging gives an empty rectangle that is too small to keep', () => {
    expect(isBigEnough(rectFromPoints({ x: 10, y: 10 }, { x: 10, y: 10 }, IMAGE))).toBe(false);
  });
});

describe('toImageCoords', () => {
  it('scales a position on a shrunken image back to image pixels', () => {
    // A 600x400 image shown at 300x200, whose top-left corner is at (20, 40) on the page.
    const shown = { left: 20, top: 40, width: 300, height: 200 };

    expect(toImageCoords(20, 40, shown, IMAGE)).toEqual({ x: 0, y: 0 });
    expect(toImageCoords(170, 140, shown, IMAGE)).toEqual({ x: 300, y: 200 });
    expect(toImageCoords(320, 240, shown, IMAGE)).toEqual({ x: 600, y: 400 });
  });
});

describe('nextSlotNo', () => {
  it('starts at 1 and continues after the highest number, even with gaps', () => {
    expect(nextSlotNo([])).toBe(1);
    expect(nextSlotNo([{ slotNo: 1 }, { slotNo: 2 }])).toBe(3);
    expect(nextSlotNo([{ slotNo: 1 }, { slotNo: 5 }])).toBe(6);
  });
});
