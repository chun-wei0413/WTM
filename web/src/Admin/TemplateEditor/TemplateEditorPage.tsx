import { useParams } from 'react-router-dom';
import { ErrorNotice } from '../../components/ErrorNotice';
import { TemplateEditor } from './TemplateEditor';
import { useTemplate } from './useTemplate';

export function TemplateEditorPage() {
  const { id = '' } = useParams();
  const { template, loadError, refresh } = useTemplate(id);

  if (loadError != null) return <div className="page"><ErrorNotice error={loadError} /></div>;
  if (!template) return <div className="page"><p className="status">載入中…</p></div>;

  // keyed by the template, so the working copy starts again from a template that has just been loaded
  return <TemplateEditor key={template.id} id={id} template={template} refresh={refresh} />;
}
