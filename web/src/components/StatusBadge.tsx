import type { TemplateStatus } from '../api/types';

const LABELS: Record<TemplateStatus, { text: string; className: string }> = {
  DRAFT: { text: '草稿', className: 'badge' },
  APPROVED: { text: '已核准', className: 'badge badge-success' },
  RETIRED: { text: '已下架', className: 'badge badge-muted' },
};

export function StatusBadge({ status }: { status: TemplateStatus }) {
  const { text, className } = LABELS[status];
  return <span className={className}>{text}</span>;
}
