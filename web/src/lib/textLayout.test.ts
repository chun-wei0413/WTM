import { describe, expect, it } from 'vitest';
import { fitText, wrapText, type Measure } from './textLayout';

/** Every character is as wide as the font size is high. */
const square: Measure = (text, size) => Array.from(text).length * size;

describe('wrapText', () => {
  it('keeps short text on one line', () => {
    expect(wrapText('你好', 100, 10, square)).toEqual(['你好']);
  });

  it('breaks Chinese anywhere, since it has no spaces', () => {
    expect(wrapText('一二三四五六七', 30, 10, square)).toEqual(['一二三', '四五六', '七']);
  });

  it('breaks Latin text at spaces and keeps words whole', () => {
    expect(wrapText('hello big world', 100, 10, square)).toEqual(['hello big', 'world']);
  });

  it('cuts a single word that is wider than the box', () => {
    expect(wrapText('abcdefghij', 40, 10, square)).toEqual(['abcd', 'efgh', 'ij']);
  });

  it('keeps the line breaks the person typed and drops trailing empty lines', () => {
    expect(wrapText('上\n下\n\n', 100, 10, square)).toEqual(['上', '下']);
  });

  it('does not start a line with a space', () => {
    expect(wrapText('aaaa bbbb', 40, 10, square)).toEqual(['aaaa', 'bbbb']);
  });
});

describe('fitText', () => {
  it('uses the largest size that fits, never more than the box height allows for one line', () => {
    // One line at size 50 is 60 high with the default line height of 1.2.
    const layout = fitText('你好', { width: 1000, height: 60 }, square);

    expect(layout.fontSize).toBe(50);
    expect(layout.lines).toEqual(['你好']);
  });

  it('shrinks the text until the wrapped lines fit the box', () => {
    const layout = fitText('一二三四五六七八', { width: 40, height: 40 }, square, { lineHeightRatio: 1 });

    expect(layout.lines.length * layout.lineHeight).toBeLessThanOrEqual(40);
    expect(layout.fontSize).toBeLessThan(40);
  });

  it('is the largest size that fits: one more would overflow', () => {
    const box = { width: 60, height: 60 };
    const text = '這是一段比較長的文字要放進去';
    const layout = fitText(text, box, square, { lineHeightRatio: 1.2 });
    const next = wrapText(text, box.width, layout.fontSize + 1, square);

    expect(layout.lines.length * layout.fontSize * 1.2).toBeLessThanOrEqual(box.height);
    expect(next.length * (layout.fontSize + 1) * 1.2).toBeGreaterThan(box.height);
  });

  it('falls back to the smallest size when nothing fits', () => {
    const layout = fitText('很長很長很長很長很長很長很長很長', { width: 20, height: 12 }, square, { minFontSize: 8 });

    expect(layout.fontSize).toBe(8);
  });

  it('respects the maximum size', () => {
    expect(fitText('a', { width: 1000, height: 1000 }, square, { maxFontSize: 64 }).fontSize).toBe(64);
  });
});
