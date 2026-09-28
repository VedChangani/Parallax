import { Navigate, Outlet, useLocation } from 'react-router';
import { LoadingState } from '../components/LoadingState.jsx';
import { useAuth } from './AuthContext.js';

/**
 * Gates every protected route (D-39): anonymous → redirected to
 * `/login?next=<the route that was actually requested>`, so a successful
 * login lands back where the user was headed rather than always at the
 * app's default page.
 */
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
