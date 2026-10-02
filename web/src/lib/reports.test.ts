import { describe, expect, it } from 'vitest';
import type { ReportCase, ReviewStatus } from '../api/types';
import { commentTooLong, diffList, isReviewing, sameText } from './reports';

const caseWith = (status: ReviewStatus | null): ReportCase => ({
  templateId: 't',
  name: 'n',
  status: 'APPROVED',
  imageUrl: '/i',
  current: { meaning: '', usageExamples: [], emotions: [], aliases: [], imageText: '', tags: [] },
  reports: [],
  weight: 1,
  neededWeight: 2,
  review: status ? { status, suggestion: null, error: null } : null,
});

describe('list diff', () => {
  it('tells what is new, what is gone and what stayed', () => {
    expect(diffList(['貓', '狗', '鳥'], ['狗', '魚'])).toEqual({ added: ['魚'], removed: ['貓', '鳥'], kept: ['狗'] });
  });

  it('shows no change for equal lists, whatever their order', () => {
    const diff = diffList(['a', 'b'], ['b', 'a']);

    expect(diff.added).toEqual([]);
    expect(diff.removed).toEqual([]);
  });

  it('copes with empty lists', () => {
    expect(diffList([], ['a'])).toEqual({ added: ['a'], removed: [], kept: [] });
    expect(diffList(['a'], [])).toEqual({ added: [], removed: ['a'], kept: [] });
  });
});

describe('waiting for the model', () => {
  it('keeps asking while a review is waiting or running', () => {
    expect(isReviewing([caseWith('DONE'), caseWith('RUNNING')])).toBe(true);
    expect(isReviewing([caseWith('PENDING')])).toBe(true);
  });

  it('stops asking once everything is answered, failed or never asked', () => {
    expect(isReviewing([caseWith('DONE'), caseWith('FAILED'), caseWith(null)])).toBe(false);
    expect(isReviewing([])).toBe(false);
  });
});

describe('comment', () => {
  it('counts characters, not bytes', () => {
    expect(commentTooLong('字'.repeat(300))).toBe(false);
    expect(commentTooLong('字'.repeat(301))).toBe(true);
  });

  it('ignores spaces around it', () => {
    expect(commentTooLong('  ' + '字'.repeat(300) + '  ')).toBe(false);
  });
});

describe('same text', () => {
  it('ignores spacing', () => {
    expect(sameText('你 好', '你好')).toBe(true);
    expect(sameText('你好', '你們好')).toBe(false);
  });
});
