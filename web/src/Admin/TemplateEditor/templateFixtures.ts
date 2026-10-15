import type { Slot, TemplateView } from '../../api/types';

export function slot(slotNo: number, patch: Partial<Slot> = {}): Slot {
  return { slotNo, role: `slot ${slotNo}`, maxChars: 10, required: true, x: 10, y: 10, width: 100, height: 40, ...patch };
}

export function template(patch: Partial<TemplateView> = {}): TemplateView {
  return {
    id: 't-1',
    name: 'Drake',
    status: 'DRAFT',
    version: 1,
    imageWidth: 400,
    imageHeight: 300,
    imageUrl: 'http://img/t-1.png',
    profile: { meaning: 'prefers one thing', usageExamples: ['no docs'], emotions: [], aliases: [], imageText: '', tags: [] },
    slots: [slot(1), slot(2)],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...patch,
  };
}
