import { Navigate, Outlet, useLocation } from 'react-router';
import { LoadingState } from '../components/LoadingState.jsx';
import { useAuth } from './AuthContext.js';

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <LoadingState label="Checking session…" />;
  }

  if (status === 'anonymous') {
    const next = encodeURIComponent(`${location.pathname}${location.search}`);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  return <Outlet />;
}
