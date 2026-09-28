import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider.jsx';
import { AppLayout } from './AppLayout.jsx';

function Bomb() {
  throw new Error('boom');
}

/** D-39: Nav renders inside AuthProvider in the real app - AppLayout alone would throw without it. */
function renderLayoutAt(path) {
  // No route here goes through RequireAuth, so Nav's identity display
  // (which only ever shows "Log in" until GET /api/auth/me resolves) is
  // the only thing that needs AuthProvider - the routed pages below render
  // immediately regardless of auth state.
  globalThis.fetch ??= vi.fn(() => Promise.resolve(new Response(null, { status: 401 })));

  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/backtests" element={<p>backtests page</p>} />
            <Route path="/strategies" element={<p>strategies page</p>} />
            <Route path="/broken" element={<Bomb />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('AppLayout', () => {
  it('renders persistent navigation exposing Backtests, Strategies, and Markets', () => {
    renderLayoutAt('/backtests');

    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Backtests' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Strategies' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Markets' })).toBeTruthy();
  });

  it('exposes a main landmark containing the routed page', () => {
    renderLayoutAt('/backtests');

    const main = screen.getByRole('main');
    expect(main).toBeTruthy();
    expect(screen.getByText('backtests page')).toBeTruthy();
  });

  it('marks the active nav link with aria-current', () => {
    renderLayoutAt('/strategies');

    expect(screen.getByRole('link', { name: 'Strategies' }).getAttribute('aria-current')).toBe('page');
    expect(screen.getByRole('link', { name: 'Backtests' }).getAttribute('aria-current')).toBeNull();
  });

  describe('when the routed page throws', () => {
    beforeEach(() => {
      vi.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('keeps navigation usable instead of taking down the whole shell', () => {
      renderLayoutAt('/broken');

      expect(screen.getByRole('navigation', { name: 'Primary' })).toBeTruthy();
      expect(screen.getByRole('link', { name: 'Backtests' })).toBeTruthy();
      expect(screen.getByText('Something went wrong')).toBeTruthy();
    });
  });
});
