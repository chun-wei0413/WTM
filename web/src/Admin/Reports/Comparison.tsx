import type { ReportCase } from '../../api/types';
import { diffList, sameText } from '../../lib/reports';
import { Field } from './Field';

/** The current description and the proposal side by side, with what changed marked. */
export function Comparison({ item, suggestion }: { item: ReportCase; suggestion: NonNullable<ReportCase['review']>['suggestion'] }) {
  if (!suggestion) return null;
  const current = item.current;
  const tags = diffList(current.tags, suggestion.tags);
  const usage = diffList(current.usageExamples, suggestion.usageExamples);
  const meaningChanged = !sameText(current.meaning, suggestion.meaning);
  const textChanged = !sameText(current.imageText, suggestion.imageText);

  return (
    <div className="compare">
      <div className="compare-col">
        <h4>目前的描述</h4>
        <Field label="意思" value={current.meaning} />
        <Field label="使用情境" list={current.usageExamples} />
        <div>
          <span className="field-label">標籤</span>
          <ul className="tag-list">
            {current.tags.map((tag) => (
              <li key={tag} className={tags.removed.includes(tag) ? 'tag-removed' : undefined}>
                {tag}
              </li>
            ))}
          </ul>
        </div>
        <Field label="圖中文字" value={current.imageText} />
      </div>
      <div className="compare-col compare-new">
        <h4>AI 的建議</h4>
        <Field label="意思" value={suggestion.meaning} changed={meaningChanged} />
        <Field label="使用情境" list={suggestion.usageExamples} added={usage.added} />
        <div>
          <span className="field-label">標籤</span>
          <ul className="tag-list">
            {suggestion.tags.map((tag) => (
              <li key={tag} className={tags.added.includes(tag) ? 'tag-added' : undefined}>
                {tag}
              </li>
            ))}
          </ul>
        </div>
        <Field label="圖中文字" value={suggestion.imageText} changed={textChanged} />
      </div>
    </div>
  );
}
