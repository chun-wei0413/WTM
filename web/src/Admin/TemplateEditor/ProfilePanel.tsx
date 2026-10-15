import type { ProfileFields } from '../../lib/templateForm';
import type { Busy } from './useTemplateEditor';

interface Props {
  fields: ProfileFields;
  retired: boolean;
  busy: Busy;
  /** Whether the description differs from the saved one. */
  dirty: boolean;
  onChange: (fields: ProfileFields) => void;
  onSave: () => void;
}

/** The description of the meme (what it means, examples, tags…), which is what makes the template findable. */
export function ProfilePanel({ fields, retired, busy, dirty, onChange, onSave }: Props) {
  return (
    <section className="card panel">
      <h2>這個梗的描述</h2>
      <p className="muted">描述就是讓這個模板「被找到」的依據,寫得越像人們實際會說的話越好。</p>
      <div className="form">
        <label>
          這個梗的意思
          <textarea
            rows={3}
            value={fields.meaning}
            disabled={retired}
            onChange={(e) => onChange({ ...fields, meaning: e.target.value })}
            placeholder="例如:拒絕或嫌棄某件事,轉而偏好另一件事。"
          />
        </label>
        <label>
          使用範例(一行一個情境)
          <textarea
            rows={4}
            value={fields.examples}
            disabled={retired}
            onChange={(e) => onChange({ ...fields, examples: e.target.value })}
            placeholder={'不想寫文件,只想直接寫程式\n拒絕早起運動,選擇繼續賴床'}
          />
        </label>
        <label>
          情緒(用頓號或逗號分隔)
          <input
            value={fields.emotions}
            disabled={retired}
            onChange={(e) => onChange({ ...fields, emotions: e.target.value })}
            placeholder="嫌棄、偏好、得意"
          />
        </label>
        <label>
          別名(用頓號或逗號分隔)
          <input
            value={fields.aliases}
            disabled={retired}
            onChange={(e) => onChange({ ...fields, aliases: e.target.value })}
            placeholder="Drake、德雷克"
          />
        </label>
        <label>
          標籤(用頓號或逗號分隔;搜尋會用到)
          <input
            value={fields.tags}
            disabled={retired}
            onChange={(e) => onChange({ ...fields, tags: e.target.value })}
            placeholder="政治、請願、選舉"
          />
        </label>
        <label>
          圖中文字(照圖上的字抄)
          <input
            value={fields.imageText}
            disabled={retired}
            onChange={(e) => onChange({ ...fields, imageText: e.target.value })}
          />
        </label>
        <div>
          <button
            type="button"
            className="button button-primary"
            onClick={onSave}
            disabled={retired || !dirty || busy !== null}
          >
            {busy === 'profile' ? '儲存中…' : '儲存描述'}
          </button>
        </div>
      </div>
    </section>
  );
}
