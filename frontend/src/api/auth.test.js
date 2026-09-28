import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getMe, login, logout, register } from './auth.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('auth api', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('getMe reads /api/auth/me and skips unauthorized handling', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ username: 'alice' }));

    const result = await getMe();

    expect(result).toEqual({ username: 'alice' });
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/auth/me');
  });

  it('getMe rejects with an ApiError on a 401 (anonymous - see AuthProvider)', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    await expect(getMe()).rejects.toMatchObject({ kind: 'api', status: 401 });
  });

  it('login posts form-urlencoded credentials to /api/auth/login', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ username: 'alice' }));

    await login('alice', 'hunter2');

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/x-www-form-urlencoded');
    expect(init.body).toBe('username=alice&password=hunter2');
  });

  it('logout posts to /api/auth/logout', async () => {
    globalThis.fetch.mockResolvedValue(new Response(null, { status: 204 }));

    await logout();

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/auth/logout');
    expect(init.method).toBe('POST');
  });

  it('register posts a JSON body to /api/auth/register', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ username: 'bob' }, 201));

    await register('bob', 'a-perfectly-fine-password');

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/auth/register');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ username: 'bob', password: 'a-perfectly-fine-password' });
  });
});
