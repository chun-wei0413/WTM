import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const ADMIN_PAGES = [
  { to: '/admin/collection', label: '圖庫收集' },
  { to: '/admin/reports', label: '意見回報' },
  { to: '/admin/templates', label: '圖庫管理' },
];

export function Layout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [adminOpen, setAdminOpen] = useState(false);

  // The menu closes when a page is chosen, and on Escape.
  useEffect(() => setAdminOpen(false), [location.pathname]);
  useEffect(() => {
    if (!adminOpen) return;
    const close = (event: KeyboardEvent) => event.key === 'Escape' && setAdminOpen(false);
    window.addEventListener('keydown', close);
    return () => window.removeEventListener('keydown', close);
  }, [adminOpen]);

  const inAdmin = location.pathname.startsWith('/admin');

  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">
          <img src="/logo-mark.svg" alt="" className="brand-mark" />
          <span className="brand-name">WTM</span>
        </NavLink>
        <nav aria-label="主選單" className="main-nav">
          <NavLink to="/" end>
            <span className="nav-icon" aria-hidden="true">
              🔍
            </span>
            <span>找梗圖</span>
          </NavLink>
          <NavLink to="/favorites">
            <span className="nav-icon" aria-hidden="true">
              ⭐
            </span>
            <span>梗圖收藏</span>
          </NavLink>
          <NavLink to="/create">
            <span className="nav-icon" aria-hidden="true">
              ✏️
            </span>
            <span>梗圖模板</span>
          </NavLink>
        </nav>
        {user?.isAdmin && (
          <div className="admin-menu">
            <button
              type="button"
              className={inAdmin ? 'button button-quiet active' : 'button button-quiet'}
              aria-expanded={adminOpen}
              aria-haspopup="true"
              onClick={() => setAdminOpen((open) => !open)}
            >
              管理 ▾
            </button>
            {adminOpen && (
              <div className="admin-menu-list" role="menu">
                {ADMIN_PAGES.map((page) => (
                  <NavLink key={page.to} to={page.to} role="menuitem">
                    {page.label}
                  </NavLink>
                ))}
              </div>
            )}
          </div>
        )}
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
