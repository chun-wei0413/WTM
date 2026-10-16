import { ErrorNotice } from '../components/ErrorNotice';
import { useLoginForm } from './useLoginForm';

/** The tabs to sign in or register, and the form of the one that is open. */
export function LoginForm() {
  const { mode, switchTo, username, setUsername, password, setPassword, busy, error, submit } = useLoginForm();

  return (
    <>
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
    </>
  );
}
