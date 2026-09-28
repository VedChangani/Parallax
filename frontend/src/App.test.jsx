import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

function jsonResponse(body) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
}

describe('App', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the full shell and redirects to /backtests by default', async () => {
    // D-39: / is now gated by RequireAuth, so GET /api/auth/me must resolve
    // before the redirect's destination page renders.
    globalThis.fetch = vi.fn((url) => {
      if (/\/api\/auth\/me$/.test(url)) return Promise.resolve(jsonResponse({ username: 'test-user' }));
      return Promise.resolve(jsonResponse([]));
    });

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Backtests' })).toBeTruthy();
    expect(screen.getByRole('navigation', { name: 'Primary' })).toBeTruthy();
    expect(screen.getByRole('main')).toBeTruthy();
  });
});
