import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { Size } from '../lib/slotGeometry';

export type Picture =
  | { state: 'loading' }
  | { state: 'error'; error: unknown }
  | { state: 'ready'; url: string; image: HTMLImageElement; size: Size; type: string };

/** Loads a picture through the application and decodes it, so its size is known and a canvas may draw it. */
export function usePicture(templateId: string | null): Picture {
  const [picture, setPicture] = useState<Picture>({ state: 'loading' });

  useEffect(() => {
    if (!templateId) return;
    let cancelled = false;
    let url: string | null = null;
    setPicture({ state: 'loading' });
    (async () => {
      const blob = await api.library.image(templateId);
      url = URL.createObjectURL(blob);
      const image = new Image();
      image.src = url;
      await image.decode();
      if (!cancelled) {
        setPicture({
          state: 'ready',
          url,
          image,
          size: { width: image.naturalWidth, height: image.naturalHeight },
          type: blob.type,
        });
      }
    })().catch((error: unknown) => !cancelled && setPicture({ state: 'error', error }));
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [templateId]);

  return picture;
}
