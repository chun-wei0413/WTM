import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import { isHttpUrl } from '../../lib/collection';

type Message = { kind: 'success' | 'info' | 'warn'; text: string };

/** Adds the picture a web address points to, with a name of the visitor's choice or none. */
export function useAddUrl() {
  const [url, setUrl] = useState('');
  const [title, setTitle] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<Message | null>(null);
  const [error, setError] = useState<unknown>(null);

  const valid = isHttpUrl(url);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const result = await api.admin.collection.addUrl(url.trim(), title.trim() || undefined);
      if (result.status === 'IMPORTED') {
        setMessage({ kind: 'success', text: '已加入圖庫,稍後會自動標記。' });
        setUrl('');
        setTitle('');
      } else if (result.status === 'DUPLICATE') {
        setMessage({ kind: 'info', text: '這張圖已經在圖庫裡了。' });
      } else {
        setMessage({ kind: 'warn', text: `這個網址的圖片不能用${result.reason ? `:${result.reason}` : ''}` });
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return { url, setUrl, title, setTitle, busy, message, error, valid, submit };
}
