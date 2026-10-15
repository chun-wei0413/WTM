import type { TemplateStatus, TemplateSummary } from '../../api/types';

export function summary(i: number, status: TemplateStatus = 'APPROVED', patch: Partial<TemplateSummary> = {}): TemplateSummary {
  return {
    id: `t-${i}`,
    name: `Meme ${i}`,
    status,
    version: 1,
    imageUrl: `http://img/${i}.png`,
    updatedAt: '2026-01-01T00:00:00Z',
    meaning: null,
    tags: [],
    sourceType: null,
    attribution: null,
    ...patch,
  };
}

/** `count` templates whose status cycles through approved, draft and retired. */
export function manySummaries(count: number): TemplateSummary[] {
  const statuses: TemplateStatus[] = ['APPROVED', 'DRAFT', 'RETIRED'];
  return Array.from({ length: count }, (_, i) => summary(i, statuses[i % 3]));
}
