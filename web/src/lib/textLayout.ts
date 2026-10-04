/**
 * Fitting text into a box: wrap it, then pick the largest font size at which it still fits. Pure
 * functions over a `measure` callback, so the page preview and the exported picture share one result.
 */

/** Width in pixels of `text` drawn at `fontSize`. */
export type Measure = (text: string, fontSize: number) => number;

interface TextLayout {
  fontSize: number;
  lines: string[];
  /** Height of one line, in pixels. */
  lineHeight: number;
}

interface FitOptions {
  maxFontSize?: number;
  minFontSize?: number;
  /** Line height as a multiple of the font size. */
  lineHeightRatio?: number;
}

const CJK = /[⺀-鿿豈-﫿＀-￯　-〿]/;

/** Chinese has no spaces, so every character may end a line; Latin words stay whole. */
function tokens(paragraph: string): string[] {
  const result: string[] = [];
  let word = '';
  for (const char of Array.from(paragraph)) {
    if (CJK.test(char)) {
      if (word) result.push(word);
      word = '';
      result.push(char);
    } else if (/\s/.test(char)) {
      if (word) result.push(word);
      word = '';
      result.push(' ');
    } else {
      word += char;
    }
  }
  if (word) result.push(word);
  return result;
}

/** Breaks one over-long word into pieces that each fit the width. */
function breakWord(word: string, maxWidth: number, fontSize: number, measure: Measure): string[] {
  const pieces: string[] = [];
  let current = '';
  for (const char of Array.from(word)) {
    if (current && measure(current + char, fontSize) > maxWidth) {
      pieces.push(current);
      current = char;
    } else {
      current += char;
    }
  }
  if (current) pieces.push(current);
  return pieces;
}

/** Splits text into lines no wider than `maxWidth`. Line breaks the person typed are kept. */
export function wrapText(text: string, maxWidth: number, fontSize: number, measure: Measure): string[] {
  const lines: string[] = [];
  for (const paragraph of text.split(/\r?\n/)) {
    let line = '';
    for (const token of tokens(paragraph)) {
      if (token === ' ' && line === '') continue; // no space at the start of a line
      const candidate = line + token;
      if (measure(candidate.trimEnd(), fontSize) <= maxWidth) {
        line = candidate;
        continue;
      }
      if (line.trim() !== '') lines.push(line.trimEnd());
      line = '';
      if (token === ' ') continue;
      if (measure(token, fontSize) <= maxWidth) {
        line = token;
      } else {
        const pieces = breakWord(token, maxWidth, fontSize, measure);
        lines.push(...pieces.slice(0, -1));
        line = pieces[pieces.length - 1] ?? '';
      }
    }
    lines.push(line.trimEnd());
  }
  // Blank lines at the end only push the text off-centre.
  while (lines.length > 1 && lines[lines.length - 1] === '') lines.pop();
  return lines;
}

/** The largest font size at which the wrapped text fits the box; the smallest allowed size when none does. */
export function fitText(
  text: string,
  box: { width: number; height: number },
  measure: Measure,
  { maxFontSize = 200, minFontSize = 8, lineHeightRatio = 1.2 }: FitOptions = {},
): TextLayout {
  const layoutAt = (fontSize: number): TextLayout => ({
    fontSize,
    lines: wrapText(text, box.width, fontSize, measure),
    lineHeight: fontSize * lineHeightRatio,
  });
  const fits = (layout: TextLayout) => layout.lines.length * layout.lineHeight <= box.height;

  let low = minFontSize;
  let high = Math.max(minFontSize, Math.min(maxFontSize, Math.floor(box.height)));
  let best = layoutAt(low);
  // Bigger text never needs fewer lines, so the largest size that fits can be found by halving.
  while (low <= high) {
    const middle = Math.floor((low + high) / 2);
    const layout = layoutAt(middle);
    if (fits(layout)) {
      best = layout;
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  return best;
}
