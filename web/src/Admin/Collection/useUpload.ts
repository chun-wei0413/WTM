import { useState, type ChangeEvent } from 'react';
import { api } from '../../api/client';
import type { IngestResult } from '../../api/types';
import { onlyImages, summarizeUploads, type UploadSummary } from '../../lib/collection';

/** Pictures sent per request, so a big folder shows progress and one bad batch does not lose the rest. */
export const UPLOAD_BATCH = 20;

/** Sends the pictures a visitor chose, batch by batch, and sums up what the library did with them. */
export function useUpload() {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [summary, setSummary] = useState<UploadSummary | null>(null);
  const [skipped, setSkipped] = useState(0);
  const [error, setError] = useState<unknown>(null);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target;
    const chosen = Array.from(input.files ?? []);
    input.value = ''; // lets the same folder be chosen again
    const files = onlyImages(chosen);
    setSkipped(chosen.length - files.length);
    setSummary(null);
    setError(null);
    if (files.length === 0) return;

    setUploading(true);
    setProgress({ done: 0, total: files.length });
    const results: IngestResult[] = [];
    try {
      for (let i = 0; i < files.length; i += UPLOAD_BATCH) {
        const batch = files.slice(i, i + UPLOAD_BATCH);
        results.push(...(await api.admin.collection.addFiles(batch)));
        setProgress({ done: Math.min(i + batch.length, files.length), total: files.length });
      }
    } catch (e) {
      setError(e);
    } finally {
      // Show what did get in, even when a later batch failed.
      setSummary(summarizeUploads(results));
      setUploading(false);
    }
  }

  return { uploading, progress, summary, skipped, error, upload };
}
