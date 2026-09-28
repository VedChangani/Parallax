import { act, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { request } from '../api/httpClient.js';
import * as immutableCache from '../api/immutableCache.js';
import { AUTH_CHANNEL_NAME, AuthProvider } from './AuthProvider.jsx';
import { useAuth } from './AuthContext.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

/** A minimal consumer exposing AuthContext's state and actions to the test DOM. */
function Consumer() {
  const { status, username, login, logout } = useAuth();
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="username">{username ?? ''}</p>
      <button onClick={() => login('alice', 'hunter2')}>login</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    immutableCache.clear();
  });

  it('resolves to anonymous when GET /api/auth/me returns 401', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'));
  });

  it('resolves to authenticated when GET /api/auth/me succeeds', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'alice' }));

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'));
    expect(screen.getByTestId('username').textContent).toBe('alice');
  });

  it('login clears the immutable cache and sets authenticated', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/login')) return Promise.resolve(jsonResponse({ username: 'alice' }));
      return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    });

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'));

    immutableCache.set('/api/strategies/1/versions/1', { some: 'data' });
    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(true);

    await act(async () => {
      screen.getByText('login').click();
    });

    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    expect(screen.getByTestId('status').textContent).toBe('authenticated');
    expect(screen.getByTestId('username').textContent).toBe('alice');
  });

  it('logout clears the immutable cache, sets anonymous, and re-bootstraps a fresh CSRF cookie', async () => {
    // D-39: logout's own CsrfLogoutHandler expires the XSRF-TOKEN cookie,
    // so AuthProvider re-bootstraps it via a second GET /api/auth/me right
    // after logging out - which, for an already-logged-out session, itself
    // resolves 401 (the third branch here).
    let loggedOut = false;
    globalThis.fetch = vi.fn((url) => {
      if (String(url).endsWith('/api/auth/logout')) {
        loggedOut = true;
        return Promise.resolve(new Response(null, { status: 204 }));
      }
      if (loggedOut) return Promise.resolve(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
      return Promise.resolve(jsonResponse({ username: 'alice' }));
    });

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'));

    immutableCache.set('/api/strategies/1/versions/1', { some: 'data' });

    await act(async () => {
      screen.getByText('logout').click();
    });

    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'));
  });

  it('an unexpected 401 from an unrelated data request clears the cache and returns to anonymous', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'alice' }));

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'));

    immutableCache.set('/api/strategies/1/versions/1', { some: 'data' });
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    await act(async () => {
      await request('/api/strategies').catch(() => {});
    });

    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'));
  });

  it('synchronizes a logout across tabs via BroadcastChannel', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ username: 'alice' }));

    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'));

    immutableCache.set('/api/strategies/1/versions/1', { some: 'data' });

    // Simulates another browser tab announcing that its identity changed
    // (that tab's own AuthProvider already logged out server-side).
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));
    const otherTabChannel = new BroadcastChannel(AUTH_CHANNEL_NAME);

    await act(async () => {
      otherTabChannel.postMessage({ type: 'identity-changed' });
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'));

    otherTabChannel.close();
  });
});
