import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { FavoritesProvider } from './favorites/FavoritesContext';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/LoginPage';
import { EditorPage } from './pages/EditorPage';
import { FavoritesPage } from './pages/FavoritesPage';
import { SearchPage } from './pages/SearchPage';
import { CollectionPage } from './pages/admin/CollectionPage';
import { ReportsPage } from './pages/admin/ReportsPage';
import { TemplateEditorPage } from './pages/admin/TemplateEditorPage';
import { TemplatesPage } from './pages/admin/TemplatesPage';

function RequireAuth() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return (
    <FavoritesProvider>
      <Outlet />
    </FavoritesProvider>
  );
}

/** Hides the admin pages from everyone else. The server enforces this too; this only keeps the screens tidy. */
function RequireAdmin() {
  const { user } = useAuth();
  if (!user?.isAdmin) return <Navigate to="/" replace />;
  return <Outlet />;
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route index element={<SearchPage />} />
          <Route path="favorites" element={<FavoritesPage />} />
          <Route path="create" element={<EditorPage />} />
          <Route element={<RequireAdmin />}>
            <Route path="admin/collection" element={<CollectionPage />} />
            <Route path="admin/reports" element={<ReportsPage />} />
            <Route path="admin/templates" element={<TemplatesPage />} />
            <Route path="admin/templates/:id" element={<TemplateEditorPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
