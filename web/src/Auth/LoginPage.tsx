import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { LoginForm } from './LoginForm';

export function LoginPage() {
  const { user } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  if (user) return <Navigate to={from} replace />;

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-brand">
          <img src="/logo-mark.svg" alt="" className="brand-mark brand-mark-large" />
          WTM
        </h1>
        <p className="auth-tagline">描述一個情境,得到合適的梗圖</p>

        <LoginForm />
      </div>
    </div>
  );
}
