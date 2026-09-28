import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider.jsx';
import { RequireAuth } from './RequireAuth.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderProtectedAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<p>login page</p>} />
          <Route element={<RequireAuth />}>
            <Route path="/secret" element={<h1>Secret page</h1>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('RequireAuth', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('redirects an anonymous visitor to /login with a next parameter', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    renderProtectedAt('/secret');

    expect(await screen.findByText('login page')).toBeTruthy();
  });

  it('renders the protected route once authenticated', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'alice' }));

    renderProtectedAt('/secret');

    expect(await screen.findByRole('heading', { name: 'Secret page' })).toBeTruthy();
  });
});
