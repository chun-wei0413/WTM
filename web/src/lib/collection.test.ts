import { describe, expect, it } from 'vitest';
import type { CollectionRun, IngestResult } from '../api/types';
import {
  changedOptions,
  describeUploads,
  hasActiveRun,
  isHttpUrl,
  onlyImages,
  runListDelay,
  sourceTypeLabel,
  summarizeUploads,
} from './collection';

const result = (status: IngestResult['status'], fileName = 'a.png', reason: string | null = null): IngestResult => ({
  fileName,
  status,
  templateId: null,
  reason,
});

const run = (status: CollectionRun['status']): CollectionRun => ({
  id: 'r',
  source: 'IMGFLIP',
  options: null,
  status,
  counts: { found: 0, imported: 0, duplicates: 0, rejected: 0, failed: 0 },
  message: null,
  startedAt: '2026-01-01T00:00:00Z',
  finishedAt: null,
});

describe('upload summary', () => {
  it('counts each outcome and keeps the reason a picture was refused', () => {
    const summary = summarizeUploads([
      result('IMPORTED'),
      result('IMPORTED'),
      result('DUPLICATE'),
      result('REJECTED', 'b.png', 'too small'),
    ]);

    expect(summary.imported).toBe(2);
    expect(summary.duplicates).toBe(1);
    expect(summary.rejected).toEqual([{ fileName: 'b.png', reason: 'too small' }]);
  });

  it('says only what happened', () => {
    expect(describeUploads(summarizeUploads([result('IMPORTED'), result('DUPLICATE')]))).toBe('新增 1 張、1 張已經在圖庫裡');
    expect(describeUploads(summarizeUploads([result('REJECTED')]))).toBe('1 張不能用');
    expect(describeUploads(summarizeUploads([]))).toBe('沒有收到任何圖片');
  });
});

describe('runs', () => {
  it('keeps asking while a run is going, and stops when none is', () => {
    expect(hasActiveRun([run('COMPLETED'), run('RUNNING')])).toBe(true);
    expect(runListDelay([run('RUNNING')])).toBe(2_000);
    expect(runListDelay([run('COMPLETED'), run('FAILED')])).toBeNull();
    expect(runListDelay([])).toBeNull();
  });
});

describe('source options', () => {
  const options = [
    { key: 'query', defaultValue: '' },
    { key: 'sort', defaultValue: 'top' },
  ];

  it('sends only what the administrator changed', () => {
    expect(changedOptions(options, { query: ' 困惑 ', sort: 'top' })).toEqual({ query: '困惑' });
  });

  it('sends nothing when everything is at its default', () => {
    expect(changedOptions(options, {})).toEqual({});
  });
});

describe('addresses and files', () => {
  it('accepts only http and https addresses', () => {
    expect(isHttpUrl('https://example.com/a.jpg')).toBe(true);
    expect(isHttpUrl(' http://example.com/a.jpg ')).toBe(true);
    expect(isHttpUrl('ftp://example.com/a.jpg')).toBe(false);
    expect(isHttpUrl('javascript:alert(1)')).toBe(false);
    expect(isHttpUrl('not an address')).toBe(false);
  });

  it('drops everything that is not a picture from a chosen folder', () => {
    const files = [
      new File([''], 'a.png', { type: 'image/png' }),
      new File([''], 'b.JPG', { type: '' }),
      new File([''], 'notes.txt', { type: 'text/plain' }),
      new File([''], 'c.svg', { type: 'image/svg+xml' }),
    ];

    expect(onlyImages(files).map((f) => f.name)).toEqual(['a.png', 'b.JPG']);
  });
});

describe('source labels', () => {
  it('names known sources and passes unknown ones through', () => {
    expect(sourceTypeLabel('UPLOAD')).toBe('手動上傳');
    expect(sourceTypeLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
    expect(sourceTypeLabel(null)).toBeNull();
  });
});
