import type { TemplateStatus, TemplateSummary } from '../api/types';
import { sourceTypeLabel } from './collection';

export type StatusFilter = TemplateStatus | 'ALL';

/** What a person typed, split into words that must all be found somewhere in an entry. */
function words(query: string): string[] {
  return query
    .toLowerCase()
    .split(/\s+/)
    .filter((word) => word.length > 0);
}

function haystack(entry: TemplateSummary): string {
  return [
    entry.name,
    entry.meaning ?? '',
    entry.tags.join(' '),
    sourceTypeLabel(entry.sourceType) ?? '',
    entry.sourceType ?? '',
    entry.attribution ?? '',
  ]
    .join(' ')
    .toLowerCase();
}

export function filterTemplates(
  entries: readonly TemplateSummary[],
  status: StatusFilter,
  query: string,
): TemplateSummary[] {
  const wanted = words(query);
  return entries.filter(
    (entry) => (status === 'ALL' || entry.status === status) && wanted.every((word) => haystack(entry).includes(word)),
  );
}

export function countByStatus(entries: readonly TemplateSummary[]): Record<StatusFilter, number> {
  const counts: Record<StatusFilter, number> = { ALL: entries.length, DRAFT: 0, APPROVED: 0, RETIRED: 0 };
  for (const entry of entries) counts[entry.status] += 1;
  return counts;
}
