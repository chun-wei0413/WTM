import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';

/** The form for a new template: a name and a picture; once created, it opens the editor of the new draft. */
export function useNewTemplate() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<unknown>(null);

  // Release the preview image when it is replaced or the page closes.
  useEffect(() => {
    if (!file) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  function onFile(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] ?? null;
    setFile(chosen);
    if (chosen && !name.trim()) setName(chosen.name.replace(/\.[^.]+$/, ''));
  }

  async function create(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    setCreating(true);
    setCreateError(null);
    try {
      const { id } = await api.admin.draftTemplate(name.trim(), file);
      navigate(`/admin/templates/${id}`);
    } catch (e) {
      setCreateError(e);
      setCreating(false);
    }
  }

  return { name, setName, file, preview, creating, createError, onFile, create };
}
