import { useState } from 'react';
import { saveBlob } from '../lib/download';
import type { TextBox } from '../lib/memeBoxes';
import { renderMeme } from '../lib/renderMeme';
import type { Picture } from './usePicture';

/** Draws the picture with its text boxes into a PNG and saves it; the meme never leaves the browser. */
export function useMemeDownload(picture: Picture, boxes: TextBox[], templateId: string) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function download() {
    if (picture.state !== 'ready') return;
    setSaving(true);
    setError(null);
    try {
      const blob = await renderMeme(picture.image, picture.size, boxes);
      saveBlob(blob, `my-meme-${templateId.slice(0, 8)}.png`);
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  return { saving, error, download };
}
