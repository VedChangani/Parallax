import { Outlet } from 'react-router';
import { ErrorBoundary } from './ErrorBoundary.jsx';
import { Nav } from './Nav.jsx';

/**
 * The persistent application shell: nav landmark + main content area.
 * Rendered once as the root layout route; every page renders into
 * `<Outlet />`. The ErrorBoundary wraps only the routed content, not Nav,
 * so a rendering error in one page never takes navigation down with it.
 */
export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <Nav />
      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-10">
        <ErrorBoundary>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  );
}
