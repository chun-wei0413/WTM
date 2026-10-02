import { friendlyError } from '../lib/errors';

export function ErrorNotice({ error, context = 'general' }: { error: unknown; context?: 'login' | 'general' }) {
  const { title, detail } = friendlyError(error, context);
  return (
    <div className="notice notice-error" role="alert">
      <strong>{title}</strong>
      {detail && <span className="notice-detail">{detail}</span>}
    </div>
  );
}
