import { FONT_FAMILY, OUTLINE_RATIO, TEXT_STYLES, placeText, type TextBox } from './memeBoxes';
import type { Size } from './slotGeometry';
import type { Measure } from './textLayout';

/** Measures text the way the browser will draw it, which is what both the preview and the export need. */
export function canvasMeasure(): Measure {
  const context = document.createElement('canvas').getContext('2d');
  return (text, fontSize) => {
    if (!context) return text.length * fontSize; // a browser without canvas cannot export anyway
    context.font = `800 ${fontSize}px ${FONT_FAMILY}`;
    return context.measureText(text).width;
  };
}

/**
 * Draws the picture and every box's text on a canvas and returns it as a PNG. Nothing leaves the
 * browser: the result is meant to be saved by the person who made it.
 */
export async function renderMeme(source: CanvasImageSource, size: Size, boxes: readonly TextBox[]): Promise<Blob> {
  const canvas = document.createElement('canvas');
  canvas.width = size.width;
  canvas.height = size.height;
  const context = canvas.getContext('2d');
  if (!context) throw new Error('This browser cannot draw pictures');

  context.drawImage(source, 0, 0, size.width, size.height);
  // Fonts load lazily; waiting here keeps the first export from falling back to another typeface.
  await document.fonts?.ready;

  const measure = canvasMeasure();
  context.textAlign = 'center';
  context.textBaseline = 'middle';
  context.lineJoin = 'round';
  for (const box of boxes) {
    const placed = placeText(box, measure);
    if (!placed) continue;
    const style = TEXT_STYLES[box.style];
    context.font = `800 ${placed.fontSize}px ${FONT_FAMILY}`;
    context.lineWidth = placed.fontSize * OUTLINE_RATIO;
    context.strokeStyle = style.outline;
    context.fillStyle = style.fill;
    for (const line of placed.lines) {
      context.strokeText(line.text, line.x, line.y);
      context.fillText(line.text, line.x, line.y);
    }
  }

  return new Promise((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('Could not save the picture'))), 'image/png'),
  );
}
