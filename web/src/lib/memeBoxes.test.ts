import { describe, expect, it } from 'vitest';
import { bottomBox, nextBoxId, placeText, type TextBox } from './memeBoxes';
import type { Measure } from './textLayout';

const square: Measure = (text, size) => Array.from(text).length * size;

const box = (over: Partial<TextBox> = {}): TextBox => ({
  id: 1,
  text: '你好',
  style: 'white',
  x: 100,
  y: 200,
  width: 400,
  height: 100,
  ...over,
});

describe('boxes', () => {
  it('numbers a new box after the highest one', () => {
    expect(nextBoxId([])).toBe(1);
    expect(nextBoxId([{ id: 1 }, { id: 4 }])).toBe(5);
  });

  it('puts a new box along the bottom, fully inside the picture', () => {
    const image = { width: 600, height: 400 };

    const b = bottomBox(1, image);

    expect(b.x).toBeGreaterThanOrEqual(0);
    expect(b.y).toBeGreaterThanOrEqual(0);
    expect(b.x + b.width).toBeLessThanOrEqual(image.width);
    expect(b.y + b.height).toBeLessThanOrEqual(image.height);
    expect(b.y).toBeGreaterThan(image.height / 2);
  });

  it('still fits a very small picture', () => {
    const b = bottomBox(1, { width: 30, height: 30 });

    expect(b.y + b.height).toBeLessThanOrEqual(30 + 20);
    expect(b.width).toBeGreaterThan(0);
  });
});

describe('placing text', () => {
  it('centres the lines in the box', () => {
    const placed = placeText(box(), square)!;

    expect(placed.lines).toHaveLength(1);
    expect(placed.lines[0]).toMatchObject({ text: '你好', x: 300, y: 250 });
  });

  it('stacks wrapped lines around the middle of the box', () => {
    const placed = placeText(box({ text: '一二三四五六七八九十', width: 50, height: 100 }), square)!;

    const ys = placed.lines.map((l) => l.y);
    expect(placed.lines.length).toBeGreaterThan(1);
    expect(ys.reduce((a, b) => a + b, 0) / ys.length).toBeCloseTo(250, 5);
    expect([...ys].sort((a, b) => a - b)).toEqual(ys);
  });

  it('draws nothing for blank text', () => {
    expect(placeText(box({ text: '   ' }), square)).toBeNull();
    expect(placeText(box({ text: '' }), square)).toBeNull();
  });
});
