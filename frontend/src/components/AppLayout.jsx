import { Outlet } from 'react-router';
import { ErrorBoundary } from './ErrorBoundary.jsx';
import { Nav } from './Nav.jsx';

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
