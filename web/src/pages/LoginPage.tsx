import { useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';

type Mode = 'login' | 'register';

export function LoginPage() {
  const { user, login, register } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  if (user) return <Navigate to={from} replace />;

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
    // On success the user is set and this page redirects, so there is nothing more to do here.
  }

  function switchTo(next: Mode) {
    setMode(next);
    setError(null);
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-brand">
          <img src="/logo-mark.svg" alt="" className="brand-mark brand-mark-large" />
          WTM
        </h1>
        <p className="auth-tagline">描述一個情境,得到合適的梗圖</p>

        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={mode === 'login'} onClick={() => switchTo('login')}>
            登入
          </button>
          <button type="button" role="tab" aria-selected={mode === 'register'} onClick={() => switchTo('register')}>
            註冊
          </button>
        </div>

        <form onSubmit={submit} className="form">
          <label>
            帳號
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              autoFocus
              required
              minLength={mode === 'register' ? 3 : undefined}
              maxLength={32}
            />
            {mode === 'register' && <span className="hint">3 到 32 個字元,可用英文字母、數字、「.」「_」「-」</span>}
          </label>
          <label>
            密碼
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={mode === 'register' ? 8 : undefined}
            />
            {mode === 'register' && <span className="hint">至少 8 個字元</span>}
          </label>

          {error != null && <ErrorNotice error={error} context={mode === 'login' ? 'login' : 'general'} />}

          <button type="submit" className="button button-primary button-block" disabled={busy}>
            {busy ? '請稍候…' : mode === 'login' ? '登入' : '建立帳號並登入'}
          </button>
        </form>
      </div>
    </div>
  );
}
