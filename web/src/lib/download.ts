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

const EXTENSIONS: Record<string, string> = { 'image/jpeg': 'jpg', 'image/gif': 'gif', 'image/png': 'png' };

/** A file name for a downloaded picture: the given base name with the extension its type calls for. */
export function fileNameFor(base: string, blob: Blob): string {
  return `${base}.${EXTENSIONS[blob.type] ?? 'png'}`;
}
