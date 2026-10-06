import { describe, expect, it } from 'vitest';
import type { IngestResult } from '../api/types';
import {
  attributionNote,
  describeUploads,
  isHttpUrl,
  onlyImages,
  sourceTypeLabel,
  summarizeUploads,
} from './collection';

const result = (status: IngestResult['status'], fileName = 'a.png', reason: string | null = null): IngestResult => ({
  fileName,
  status,
  templateId: null,
  reason,
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

describe('attribution', () => {
  it('leaves out a credit that only repeats the source', () => {
    expect(attributionNote('Imgflip', 'Imgflip')).toBeNull();
    expect(attributionNote('Imgflip', 'imgflip ')).toBeNull();
  });

  it('keeps a credit that says more', () => {
    expect(attributionNote('維基共享資源', 'Db5man · Wikimedia Commons')).toBe('Db5man · Wikimedia Commons');
    expect(attributionNote(null, 'someone')).toBe('someone');
  });

  it('has nothing to show without a credit', () => {
    expect(attributionNote('Imgflip', null)).toBeNull();
    expect(attributionNote('Imgflip', '  ')).toBeNull();
  });
});
