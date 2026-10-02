import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          meme<span>hub</span>
        </NavLink>
        <nav aria-label="主選單">
          <NavLink to="/" end>
            產生梗圖
          </NavLink>
          <NavLink to="/memes">我的梗圖</NavLink>
          {user?.isAdmin && <NavLink to="/admin/templates">模板管理</NavLink>}
        </nav>
        <div className="topbar-user">
          <span className="username" title={user?.isAdmin ? '管理員' : undefined}>
            {user?.username}
            {user?.isAdmin && <span className="badge badge-accent">管理員</span>}
          </span>
          <button type="button" className="button button-quiet" onClick={logout}>
            登出
          </button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
