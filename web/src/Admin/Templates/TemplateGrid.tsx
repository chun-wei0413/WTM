import type { TemplateSummary } from '../../api/types';
import { TemplateCard } from './TemplateCard';

interface Props {
  /** Every template that matches, of which the first `shown` are drawn. */
  matching: TemplateSummary[];
  shown: number;
  onShowMore: () => void;
}

/** The matching templates as cards, a page at a time. */
export function TemplateGrid({ matching, shown, onShowMore }: Props) {
  return (
    <>
      <p className="muted">
        {matching.length} 張{matching.length > shown && `,先顯示 ${shown} 張`}
      </p>
      <div className="grid grid-small">
        {matching.slice(0, shown).map((t) => (
          <TemplateCard key={t.id} template={t} />
        ))}
      </div>
      {matching.length > shown && (
        <div className="actions">
          <button type="button" className="button" onClick={onShowMore}>
            顯示更多(還有 {matching.length - shown} 張)
          </button>
        </div>
      )}
    </>
  );
}
