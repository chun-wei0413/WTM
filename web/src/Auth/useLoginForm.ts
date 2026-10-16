import { useState, type FormEvent } from 'react';
import { useAuth } from './AuthContext';

export type Mode = 'login' | 'register';

/** The sign-in and the create-an-account forms share their fields; `mode` says which one is shown. */
export function useLoginForm() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await (mode === 'login' ? login(username.trim(), password) : register(username.trim(), password));
    } catch (e) {
      setError(e);
      setBusy(false);
    }
    // On success the user is set and the page redirects, so there is nothing more to do here.
  }

  function switchTo(next: Mode) {
    setMode(next);
    setError(null);
  }

  return { mode, switchTo, username, setUsername, password, setPassword, busy, error, submit };
}
