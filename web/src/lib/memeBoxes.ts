import { fitText, type Measure } from './textLayout';
import type { Rect, Size } from './slotGeometry';

/** The looks a person can choose for a piece of text. The outline keeps it readable on any picture. */
export const TEXT_STYLES = {
  white: { label: '白字黑邊', fill: '#ffffff', outline: '#000000' },
  black: { label: '黑字白邊', fill: '#000000', outline: '#ffffff' },
  yellow: { label: '黃字黑邊', fill: '#ffd23f', outline: '#000000' },
  red: { label: '紅字白邊', fill: '#e63946', outline: '#ffffff' },
} as const;

export type TextStyleId = keyof typeof TEXT_STYLES;

export const TEXT_STYLE_IDS = Object.keys(TEXT_STYLES) as TextStyleId[];

export const FONT_FAMILY = "'Noto Sans TC', 'Microsoft JhengHei', 'PingFang TC', Impact, sans-serif";

/** The outline is this fraction of the font size wide. */
export const OUTLINE_RATIO = 0.14;

export interface TextBox extends Rect {
  id: number;
  text: string;
  style: TextStyleId;
}

export interface PlacedLine {
  text: string;
  /** Horizontal centre and vertical centre of the line, in image pixels. */
  x: number;
  y: number;
}

export interface PlacedText {
  fontSize: number;
  lines: PlacedLine[];
}

export function nextBoxId(boxes: ReadonlyArray<{ id: number }>): number {
  return boxes.reduce((highest, box) => Math.max(highest, box.id), 0) + 1;
}

/** A new box along the bottom of the picture, where captions usually go. */
export function bottomBox(id: number, image: Size): TextBox {
  const width = Math.round(image.width * 0.9);
  const height = Math.max(20, Math.round(image.height * 0.18));
  return {
    id,
    text: '輸入文字',
    style: 'white',
    x: Math.round((image.width - width) / 2),
    y: Math.max(0, image.height - height - Math.round(image.height * 0.03)),
    width,
    height,
  };
}

/** Where each line of a box's text is drawn: wrapped, shrunk to fit, centred in the box. */
export function placeText(box: TextBox, measure: Measure): PlacedText | null {
  if (box.text.trim() === '') return null;
  const layout = fitText(box.text, box, measure);
  const blockHeight = layout.lines.length * layout.lineHeight;
  const top = box.y + (box.height - blockHeight) / 2;
  return {
    fontSize: layout.fontSize,
    lines: layout.lines.map((text, i) => ({
      text,
      x: box.x + box.width / 2,
      y: top + (i + 0.5) * layout.lineHeight,
    })),
  };
}
