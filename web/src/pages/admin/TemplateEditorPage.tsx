import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import type { Slot, TemplateView } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { SlotEditor } from '../../components/SlotEditor';
import { StatusBadge } from '../../components/StatusBadge';
import { clampRect } from '../../lib/slotGeometry';
import {
  diffSlots,
  fieldsToProfile,
  hasChanges,
  profileIsComplete,
  profileToFields,
  slotProblem,
  type ProfileFields,
} from '../../lib/templateForm';

type Busy = null | 'slots' | 'profile' | 'approve' | 'retire';

const EMPTY_FIELDS: ProfileFields = { meaning: '', examples: '', emotions: '', aliases: '', imageText: '', tags: '' };

export function TemplateEditorPage() {
  const { id = '' } = useParams();
  const [template, setTemplate] = useState<TemplateView | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [fields, setFields] = useState<ProfileFields>(EMPTY_FIELDS);
  const [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const [error, setError] = useState<unknown>(null);
  const [notice, setNotice] = useState<string | null>(null);

  /** Reads the template again. Each part of the working copy is only replaced when asked, so saving one part never throws away unsaved edits to the other. */
  const refresh = useCallback(
    async (replace: { slots?: boolean; profile?: boolean } = {}) => {
      const fresh = await api.admin.getTemplate(id);
      setTemplate(fresh);
      if (replace.slots) {
        setSlots(fresh.slots);
        setSelected((current) => (fresh.slots.some((s) => s.slotNo === current) ? current : null));
      }
      if (replace.profile) setFields(profileToFields(fresh.profile));
    },
    [id],
  );

  useEffect(() => {
    refresh({ slots: true, profile: true }).catch(setLoadError);
  }, [refresh]);

  if (loadError != null) return <div className="page"><ErrorNotice error={loadError} /></div>;
  if (!template) return <div className="page"><p className="status">載入中…</p></div>;

  const retired = template.status === 'RETIRED';
  const imageSize = { width: template.imageWidth, height: template.imageHeight };
  const changes = diffSlots(template.slots, slots);
  const slotsDirty = hasChanges(changes);
  const problem = slotProblem(slots);
  const draftProfile = fieldsToProfile(fields);
  const profileDirty = JSON.stringify(draftProfile) !== JSON.stringify(fieldsToProfile(profileToFields(template.profile)));
  const savedProfileComplete = profileIsComplete(template.profile);
  const selectedSlot = slots.find((s) => s.slotNo === selected) ?? null;
  const anyUnsaved = slotsDirty || profileDirty;

  async function run(kind: Exclude<Busy, null>, work: () => Promise<void>) {
    setBusy(kind);
    setError(null);
    setNotice(null);
    try {
      await work();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(null);
    }
  }

  const saveSlots = () =>
    run('slots', async () => {
      try {
        // Removals first, so a number freed by deleting a slot is never in the way of a new one.
        for (const slotNo of changes.remove) await api.admin.removeSlot(id, slotNo);
        for (const slot of changes.change) await api.admin.redefineSlot(id, slot);
        for (const slot of changes.add) await api.admin.defineSlot(id, slot);
        await refresh({ slots: true });
        setNotice('文字格已儲存');
      } catch (e) {
        // Some steps may have gone through; show what the server really has before reporting the problem.
        await refresh({ slots: true }).catch(() => undefined);
        throw e;
      }
    });

  const saveProfile = () =>
    run('profile', async () => {
      await api.admin.saveProfile(id, draftProfile);
      await refresh({ profile: true });
      setNotice('描述已儲存');
    });

  const approve = () =>
    run('approve', async () => {
      await api.admin.approve(id);
      await refresh();
      setNotice('模板已核准。搜尋索引會在約 15 秒內更新,也可以到列表頁按「同步搜尋索引」立刻更新。');
    });

  const retire = () => {
    if (!window.confirm('下架後,這個模板就不能再用來做新的梗圖,而且無法恢復。確定要下架嗎?')) return;
    void run('retire', async () => {
      await api.admin.retire(id);
      await refresh();
      setNotice('模板已下架');
    });
  };

  function updateSlot(slotNo: number, patch: Partial<Slot>) {
    setSlots((current) =>
      current.map((s) => {
        if (s.slotNo !== slotNo) return s;
        const merged = { ...s, ...patch };
        const touchesRect = 'x' in patch || 'y' in patch || 'width' in patch || 'height' in patch;
        return touchesRect ? { ...merged, ...clampRect(merged, imageSize) } : merged;
      }),
    );
  }

  function removeSlot(slotNo: number) {
    setSlots((current) => current.filter((s) => s.slotNo !== slotNo));
    setSelected(null);
  }

  const number = (value: string) => (value === '' ? 0 : Number(value));

  return (
    <div className="page editor">
      <div className="page-header">
        <div>
          <Link to="/admin/templates" className="back">← 回到模板列表</Link>
          <h1>
            {template.name} <StatusBadge status={template.status} />
          </h1>
          <p className="muted">
            版本 {template.version} · 圖片 {template.imageWidth} × {template.imageHeight}
          </p>
        </div>
      </div>

      {error != null && <ErrorNotice error={error} />}
      {notice && <div className="notice notice-success" role="status">{notice}</div>}
      {retired && <div className="notice">這個模板已下架,不能再修改。</div>}

      <div className="editor-layout">
        <section className="editor-canvas">
          <SlotEditor
            imageUrl={template.imageUrl}
            imageSize={imageSize}
            slots={slots}
            selected={selected}
            disabled={retired || busy !== null}
            onSelect={setSelected}
            onChange={setSlots}
          />
          <p className="hint">
            在圖上<strong>拖曳空白處</strong>新增文字格;點選文字格後可<strong>拖曳</strong>移動、拖曳邊角縮放,
            <kbd>Delete</kbd> 刪除,方向鍵微調(加 <kbd>Shift</kbd> 一次 10 像素)。沒有文字格也可以核准,例如圖片本身已經有字的梗圖。
          </p>
        </section>

        <aside className="editor-panels">
          <section className="card panel">
            <h2>文字格({slots.length})</h2>
            {slots.length === 0 && <p className="muted">還沒有文字格。</p>}
            <ul className="slot-list">
              {slots.map((s) => (
                <li key={s.slotNo}>
                  <button
                    type="button"
                    className={s.slotNo === selected ? 'slot-item active' : 'slot-item'}
                    onClick={() => setSelected(s.slotNo)}
                  >
                    <span className="slot-number">{s.slotNo}</span>
                    <span>{s.role || '(未命名)'}</span>
                    <span className="muted">≤ {s.maxChars} 字{s.required ? '' : ' · 選填'}</span>
                  </button>
                </li>
              ))}
            </ul>

            {selectedSlot && (
              <div className="form slot-form">
                <label>
                  用途名稱
                  <input
                    value={selectedSlot.role}
                    disabled={retired}
                    onChange={(e) => updateSlot(selectedSlot.slotNo, { role: e.target.value })}
                    placeholder="例如:被拒絕的事物"
                  />
                </label>
                <div className="row">
                  <label>
                    字數上限
                    <input
                      type="number"
                      min={1}
                      value={selectedSlot.maxChars}
                      disabled={retired}
                      onChange={(e) => updateSlot(selectedSlot.slotNo, { maxChars: number(e.target.value) })}
                    />
                  </label>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={selectedSlot.required}
                      disabled={retired}
                      onChange={(e) => updateSlot(selectedSlot.slotNo, { required: e.target.checked })}
                    />
                    必填
                  </label>
                </div>
                <div className="row row-4">
                  {(['x', 'y', 'width', 'height'] as const).map((key) => (
                    <label key={key}>
                      {{ x: 'X', y: 'Y', width: '寬', height: '高' }[key]}
                      <input
                        type="number"
                        value={selectedSlot[key]}
                        disabled={retired}
                        onChange={(e) => updateSlot(selectedSlot.slotNo, { [key]: number(e.target.value) })}
                      />
                    </label>
                  ))}
                </div>
                <div>
                  <button type="button" className="button button-danger" disabled={retired} onClick={() => removeSlot(selectedSlot.slotNo)}>
                    刪除這個文字格
                  </button>
                </div>
              </div>
            )}

            {template.status === 'APPROVED' && slotsDirty && (
              <div className="notice">
                這個模板已核准:每一項文字格的變更都會產生新版本(目前是 v{template.version})。已經做好的梗圖不受影響。
              </div>
            )}
            {problem && slotsDirty && <p className="field-error">{problem}</p>}
            <button
              type="button"
              className="button button-primary"
              onClick={saveSlots}
              disabled={retired || !slotsDirty || problem !== null || busy !== null}
            >
              {busy === 'slots' ? '儲存中…' : '儲存文字格'}
            </button>
          </section>

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
                  onChange={(e) => setFields({ ...fields, meaning: e.target.value })}
                  placeholder="例如:拒絕或嫌棄某件事,轉而偏好另一件事。"
                />
              </label>
              <label>
                使用範例(一行一個情境)
                <textarea
                  rows={4}
                  value={fields.examples}
                  disabled={retired}
                  onChange={(e) => setFields({ ...fields, examples: e.target.value })}
                  placeholder={'不想寫文件,只想直接寫程式\n拒絕早起運動,選擇繼續賴床'}
                />
              </label>
              <label>
                情緒(用頓號或逗號分隔)
                <input
                  value={fields.emotions}
                  disabled={retired}
                  onChange={(e) => setFields({ ...fields, emotions: e.target.value })}
                  placeholder="嫌棄、偏好、得意"
                />
              </label>
              <label>
                別名(用頓號或逗號分隔)
                <input
                  value={fields.aliases}
                  disabled={retired}
                  onChange={(e) => setFields({ ...fields, aliases: e.target.value })}
                  placeholder="Drake、德雷克"
                />
              </label>
              <label>
                標籤(用頓號或逗號分隔;搜尋會用到)
                <input
                  value={fields.tags}
                  disabled={retired}
                  onChange={(e) => setFields({ ...fields, tags: e.target.value })}
                  placeholder="政治、請願、選舉"
                />
              </label>
              <label>
                圖中文字(照圖上的字抄)
                <input
                  value={fields.imageText}
                  disabled={retired}
                  onChange={(e) => setFields({ ...fields, imageText: e.target.value })}
                />
              </label>
              <div>
                <button
                  type="button"
                  className="button button-primary"
                  onClick={saveProfile}
                  disabled={retired || !profileDirty || busy !== null}
                >
                  {busy === 'profile' ? '儲存中…' : '儲存描述'}
                </button>
              </div>
            </div>
          </section>

          <section className="card panel">
            <h2>發佈</h2>
            <ul className="checklist">
              <li className={template.profile.meaning.trim() ? 'ok' : ''}>已填寫這個梗的意思</li>
              <li className={template.profile.usageExamples.length > 0 ? 'ok' : ''}>至少有一個使用範例</li>
              <li className="optional">文字格是選填的</li>
            </ul>
            {anyUnsaved && !retired && <p className="muted">有尚未儲存的變更,請先儲存。</p>}
            <div className="actions">
              <button
                type="button"
                className="button button-primary"
                onClick={approve}
                disabled={template.status !== 'DRAFT' || !savedProfileComplete || anyUnsaved || busy !== null}
              >
                {busy === 'approve' ? '核准中…' : '核准'}
              </button>
              <button type="button" className="button button-danger" onClick={retire} disabled={retired || busy !== null}>
                {busy === 'retire' ? '下架中…' : '下架'}
              </button>
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
