import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { CreatePage } from './pages/CreatePage';
import { LoginPage } from './pages/LoginPage';
import { MyMemesPage } from './pages/MyMemesPage';
import { SearchPage } from './pages/SearchPage';
import { CollectionPage } from './pages/admin/CollectionPage';
import { TemplateEditorPage } from './pages/admin/TemplateEditorPage';
import { TemplatesPage } from './pages/admin/TemplatesPage';

function RequireAuth() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
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
          <Route path="generate" element={<CreatePage />} />
          <Route path="memes" element={<MyMemesPage />} />
          <Route element={<RequireAdmin />}>
            <Route path="admin/collection" element={<CollectionPage />} />
            <Route path="admin/templates" element={<TemplatesPage />} />
            <Route path="admin/templates/:id" element={<TemplateEditorPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
