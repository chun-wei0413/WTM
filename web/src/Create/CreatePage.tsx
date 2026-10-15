import { useSearchParams } from 'react-router-dom';
import { MemeEditor } from './MemeEditor';
import { TemplatePicker } from './TemplatePicker';

/** Making a meme: pick a picture among the favorites, then put text on it. */
export function CreatePage() {
  const [params] = useSearchParams();
  const from = params.get('from');
  return from ? <MemeEditor key={from} templateId={from} /> : <TemplatePicker />;
}
