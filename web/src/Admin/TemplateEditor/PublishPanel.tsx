import type { TemplateView } from '../../api/types';
import type { Busy } from './useTemplateEditor';

interface Props {
  template: TemplateView;
  retired: boolean;
  busy: Busy;
  /** Whether the saved description has what a template needs before it can be approved. */
  profileComplete: boolean;
  /** Whether there are edits that have not been saved. */
  unsaved: boolean;
  onApprove: () => void;
  onRetire: () => void;
}

/** What is still missing before the template can be approved, and the buttons to approve or retire it. */
export function PublishPanel({ template, retired, busy, profileComplete, unsaved, onApprove, onRetire }: Props) {
  return (
    <section className="card panel">
      <h2>發佈</h2>
      <ul className="checklist">
        <li className={template.profile.meaning.trim() ? 'ok' : ''}>已填寫這個梗的意思</li>
        <li className={template.profile.usageExamples.length > 0 ? 'ok' : ''}>至少有一個使用範例</li>
        <li className="optional">文字格是選填的</li>
      </ul>
      {unsaved && !retired && <p className="muted">有尚未儲存的變更,請先儲存。</p>}
      <div className="actions">
        <button
          type="button"
          className="button button-primary"
          onClick={onApprove}
          disabled={template.status !== 'DRAFT' || !profileComplete || unsaved || busy !== null}
        >
          {busy === 'approve' ? '核准中…' : '核准'}
        </button>
        <button type="button" className="button button-danger" onClick={onRetire} disabled={retired || busy !== null}>
          {busy === 'retire' ? '下架中…' : '下架'}
        </button>
      </div>
    </section>
  );
}
