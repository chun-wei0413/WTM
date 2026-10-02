/** Saves a blob as a file by clicking a temporary link. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Give the browser a moment to start the download before the link is released.
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

export function fileNameFor(memeId: string, blob: Blob): string {
  return `meme-${memeId.slice(0, 8)}.${blob.type === 'image/jpeg' ? 'jpg' : 'png'}`;
}
