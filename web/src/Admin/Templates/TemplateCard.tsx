import { Link } from 'react-router-dom';
import type { TemplateSummary } from '../../api/types';
import { StatusBadge } from '../../components/StatusBadge';
import { attributionNote, sourceTypeLabel } from '../../lib/collection';

/** One template in the list: its picture, name, status, meaning and where it came from; it opens the editor. */
export function TemplateCard({ template: t }: { template: TemplateSummary }) {
  return (
    <Link
      to={`/admin/templates/${t.id}`}
      className={t.status === 'RETIRED' ? 'card template-card picker-card retired' : 'card template-card picker-card'}
    >
      <img src={t.imageUrl} alt="" loading="lazy" />
      <div className="card-body">
        <div className="card-title">
          <span>{t.name}</span>
          <StatusBadge status={t.status} />
        </div>
        {t.meaning && <p className="tile-meaning">{t.meaning}</p>}
        <span className="muted">
          {sourceTypeLabel(t.sourceType) ?? '手動建立'}
          {attributionNote(sourceTypeLabel(t.sourceType), t.attribution) &&
            ` · ${attributionNote(sourceTypeLabel(t.sourceType), t.attribution)}`}
        </span>
      </div>
    </Link>
  );
}
