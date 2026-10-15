import type { AutomaticEntry, ReportCase } from '../../api/types';

type Review = NonNullable<ReportCase['review']>;

export function reportCase(patch: Partial<ReportCase> = {}): ReportCase {
  return {
    templateId: 't-1',
    name: 'Drake',
    status: 'APPROVED',
    imageUrl: 'http://img/t-1.png',
    current: { meaning: 'prefers one thing', usageExamples: ['no docs', 'sleep in'], emotions: [], aliases: [], imageText: 'NO YES', tags: ['drake', 'old tag'] },
    reports: [
      { id: 'r-1', templateId: 't-1', username: 'ann', reason: 'WRONG_TAGS', comment: 'tags are off', createdAt: '2026-10-01T10:00:00Z' },
      { id: 'r-2', templateId: 't-1', username: 'bob', reason: 'OTHER', comment: '', createdAt: '2026-10-02T10:00:00Z' },
    ],
    review: null,
    weight: 1,
    neededWeight: 3,
    ...patch,
  };
}

export function doneReview(patch: Partial<NonNullable<Review['suggestion']>> = {}): Review {
  return {
    status: 'DONE',
    error: null,
    suggestion: {
      isMeme: true,
      meaning: 'prefers one thing over another',
      usageExamples: ['no docs', 'a new example'],
      emotions: [],
      tags: ['drake', 'new tag'],
      imageText: 'NO YES',
      reasoning: 'the tags missed the format',
      ...patch,
    },
  };
}

export function automaticEntry(patch: Partial<AutomaticEntry> = {}): AutomaticEntry {
  return {
    templateId: 't-9',
    name: 'Distracted',
    imageUrl: 'http://img/t-9.png',
    action: 'APPLIED',
    reports: 3,
    note: 'the proposal fits',
    at: '2026-10-10T08:00:00Z',
    canUndo: true,
    ...patch,
  };
}
