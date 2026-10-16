import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { CollectionPage } from './Admin/Collection/CollectionPage';
import { ReportsPage } from './Admin/Reports/ReportsPage';
import { TemplateEditorPage } from './Admin/TemplateEditor/TemplateEditorPage';
import { TemplatesPage } from './Admin/Templates/TemplatesPage';
import { useAuth } from './Auth/AuthContext';
import { LoginPage } from './Auth/LoginPage';
import { Layout } from './components/Layout';
import { CreatePage } from './Create/CreatePage';
import { FavoritesProvider } from './Favorites/FavoritesContext';
import { FavoritesPage } from './Favorites/FavoritesPage';
import { SearchPage } from './Search/SearchPage';

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
          <Route path="create" element={<CreatePage />} />
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
