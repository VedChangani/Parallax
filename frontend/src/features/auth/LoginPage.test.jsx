import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthProvider.jsx';
import { LoginPage } from './LoginPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<h1>Backtests</h1>} />
          <Route path="/some/protected/page" element={<h1>Protected page</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

function submitLogin(username, password) {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: /log in/i }));
}

describe('LoginPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a generic message on a failed login, regardless of the reason', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse({ title: 'Unauthorized', status: 401, detail: 'invalid username or password' }, 401),
    );

    renderAt('/login');
    await waitFor(() => expect(screen.getByLabelText('Username')).toBeTruthy());

    submitLogin('someone', 'wrong-password');

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toBe('Invalid username or password.');
  });

  it('redirects to / on a successful login with no next parameter', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/login')) return Promise.resolve(jsonResponse({ username: 'alice' }));
      return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    });

    renderAt('/login');
    await waitFor(() => expect(screen.getByLabelText('Username')).toBeTruthy());

    submitLogin('alice', 'hunter2');

    expect(await screen.findByRole('heading', { name: 'Backtests' })).toBeTruthy();
  });

  it('redirects to the requested next route on a successful login', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/login')) return Promise.resolve(jsonResponse({ username: 'alice' }));
      return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    });

    renderAt('/login?next=%2Fsome%2Fprotected%2Fpage');
    await waitFor(() => expect(screen.getByLabelText('Username')).toBeTruthy());

    submitLogin('alice', 'hunter2');

    expect(await screen.findByRole('heading', { name: 'Protected page' })).toBeTruthy();
  });

  // M-5: an unvalidated `next` is an open-redirect vector - only a same-origin
  // in-app path is ever honored.

  it('falls back to / when next is an absolute URL', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/login')) return Promise.resolve(jsonResponse({ username: 'alice' }));
      return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    });

    renderAt(`/login?next=${encodeURIComponent('https://evil.example')}`);
    await waitFor(() => expect(screen.getByLabelText('Username')).toBeTruthy());

    submitLogin('alice', 'hunter2');

    expect(await screen.findByRole('heading', { name: 'Backtests' })).toBeTruthy();
  });

  it('falls back to / when next is protocol-relative', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/login')) return Promise.resolve(jsonResponse({ username: 'alice' }));
      return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    });

    renderAt(`/login?next=${encodeURIComponent('//evil.example')}`);
    await waitFor(() => expect(screen.getByLabelText('Username')).toBeTruthy());

    submitLogin('alice', 'hunter2');

    expect(await screen.findByRole('heading', { name: 'Backtests' })).toBeTruthy();
  });

  it('falls back to / when next has no leading slash', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/login')) return Promise.resolve(jsonResponse({ username: 'alice' }));
      return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    });

    renderAt(`/login?next=${encodeURIComponent('evil.example')}`);
    await waitFor(() => expect(screen.getByLabelText('Username')).toBeTruthy());

    submitLogin('alice', 'hunter2');

    expect(await screen.findByRole('heading', { name: 'Backtests' })).toBeTruthy();
  });
});
