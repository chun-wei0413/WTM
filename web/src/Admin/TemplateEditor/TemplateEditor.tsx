import { Link } from 'react-router-dom';
import type { TemplateView } from '../../api/types';
import { ErrorNotice } from '../../components/ErrorNotice';
import { SlotEditor } from '../../components/SlotEditor';
import { StatusBadge } from '../../components/StatusBadge';
import { ProfilePanel } from './ProfilePanel';
import { PublishPanel } from './PublishPanel';
import { SlotsPanel } from './SlotsPanel';
import { useTemplateEditor } from './useTemplateEditor';

interface Props {
  id: string;
  template: TemplateView;
  refresh: () => Promise<TemplateView>;
}

/** A loaded template: the picture with its slots on one side, the slot, description and publish panels on the other. */
export function TemplateEditor({ id, template, refresh }: Props) {
  const editor = useTemplateEditor(id, template, refresh);
  const { retired, busy } = editor;

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

      {editor.error != null && <ErrorNotice error={editor.error} />}
      {editor.notice && <div className="notice notice-success" role="status">{editor.notice}</div>}
      {retired && <div className="notice">這個模板已下架,不能再修改。</div>}

      <div className="editor-layout">
        <section className="editor-canvas">
          <SlotEditor
            imageUrl={template.imageUrl}
            imageSize={editor.imageSize}
            slots={editor.slots}
            selected={editor.selected}
            disabled={retired || busy !== null}
            onSelect={editor.setSelected}
            onChange={editor.setSlots}
          />
          <p className="hint">
            在圖上<strong>拖曳空白處</strong>新增文字格;點選文字格後可<strong>拖曳</strong>移動、拖曳邊角縮放,
            <kbd>Delete</kbd> 刪除,方向鍵微調(加 <kbd>Shift</kbd> 一次 10 像素)。沒有文字格也可以核准,例如圖片本身已經有字的梗圖。
          </p>
        </section>

        <aside className="editor-panels">
          <SlotsPanel
            slots={editor.slots}
            selected={editor.selected}
            selectedSlot={editor.selectedSlot}
            retired={retired}
            busy={busy}
            status={template.status}
            version={template.version}
            dirty={editor.slotsDirty}
            problem={editor.problem}
            onSelect={editor.setSelected}
            onChange={editor.updateSlot}
            onRemove={editor.removeSlot}
            onSave={editor.saveSlots}
          />
          <ProfilePanel
            fields={editor.fields}
            retired={retired}
            busy={busy}
            dirty={editor.profileDirty}
            onChange={editor.setFields}
            onSave={editor.saveProfile}
          />
          <PublishPanel
            template={template}
            retired={retired}
            busy={busy}
            profileComplete={editor.savedProfileComplete}
            unsaved={editor.anyUnsaved}
            onApprove={editor.approve}
            onRetire={editor.retire}
          />
        </aside>
      </div>
    </div>
  );
}
