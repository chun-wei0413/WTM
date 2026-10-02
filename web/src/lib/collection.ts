import type { CollectionRun, IngestResult, RunStatus } from '../api/types';

/** The sources an administrator can pick; the ids the server uses are shown in Chinese. */
const SOURCE_TYPE_LABELS: Record<string, string> = {
  IMGFLIP: 'Imgflip',
  WIKIMEDIA: '維基共享資源',
  PTT: 'PTT',
  UPLOAD: '手動上傳',
  URL: '貼上網址',
  INBOX: '收件匣資料夾',
};

export function sourceTypeLabel(sourceType: string | null): string | null {
  if (!sourceType) return null;
  return SOURCE_TYPE_LABELS[sourceType] ?? sourceType;
}

export interface UploadSummary {
  imported: number;
  duplicates: number;
  rejected: Array<{ fileName: string; reason: string | null }>;
}

export function summarizeUploads(results: readonly IngestResult[]): UploadSummary {
  return {
    imported: results.filter((r) => r.status === 'IMPORTED').length,
    duplicates: results.filter((r) => r.status === 'DUPLICATE').length,
    rejected: results.filter((r) => r.status === 'REJECTED').map((r) => ({ fileName: r.fileName, reason: r.reason })),
  };
}

/** One sentence a person can read: "新增 3 張、2 張已經有了、1 張不能用". */
export function describeUploads(summary: UploadSummary): string {
  const parts: string[] = [];
  if (summary.imported > 0) parts.push(`新增 ${summary.imported} 張`);
  if (summary.duplicates > 0) parts.push(`${summary.duplicates} 張已經在圖庫裡`);
  if (summary.rejected.length > 0) parts.push(`${summary.rejected.length} 張不能用`);
  return parts.length > 0 ? parts.join('、') : '沒有收到任何圖片';
}

export const RUN_STATUS_LABELS: Record<RunStatus, string> = {
  RUNNING: '進行中',
  COMPLETED: '已完成',
  FAILED: '失敗',
};

export function hasActiveRun(runs: readonly CollectionRun[]): boolean {
  return runs.some((run) => run.status === 'RUNNING');
}

/** How often to ask again for the run list: often while something is running, rarely otherwise. */
export function runListDelay(runs: readonly CollectionRun[]): number | null {
  return hasActiveRun(runs) ? 2_000 : null;
}

/** The options of a source as the server wants them: only the ones that differ from the default. */
export function changedOptions(
  options: ReadonlyArray<{ key: string; defaultValue: string }>,
  values: Readonly<Record<string, string>>,
): Record<string, string> {
  const result: Record<string, string> = {};
  for (const { key, defaultValue } of options) {
    const value = (values[key] ?? defaultValue).trim();
    if (value !== '' && value !== defaultValue) result[key] = value;
  }
  return result;
}

/** Accepts only addresses a browser would open; the server checks them again. */
export function isHttpUrl(text: string): boolean {
  try {
    const url = new URL(text.trim());
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

/** Picks the picture files out of a chosen folder or a drop, ignoring everything else. */
export function onlyImages(files: readonly File[]): File[] {
  return files.filter((file) => /^image\/(png|jpe?g|gif)$/.test(file.type) || /\.(png|jpe?g|gif)$/i.test(file.name));
}

/** Who made it, unless that only repeats the name of the source ("Imgflip" credited to "Imgflip"). */
export function attributionNote(sourceLabel: string | null, attribution: string | null): string | null {
  const text = attribution?.trim();
  if (!text) return null;
  return sourceLabel && text.toLowerCase() === sourceLabel.toLowerCase() ? null : text;
}
