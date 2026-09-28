import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './apiError.js';
import { request, setUnauthorizedHandler } from './httpClient.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function clearCookies() {
  document.cookie.split(';').forEach((cookie) => {
    const name = cookie.split('=')[0].trim();
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
}

describe('request', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('parses a successful JSON response', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ id: 1, name: 'RSI reversion' }));

    const result = await request('/api/strategies/1');

    expect(result).toEqual({ id: 1, name: 'RSI reversion' });
  });

  it('returns undefined for an empty successful response', async () => {
    globalThis.fetch.mockResolvedValue(new Response(null, { status: 204 }));

    const result = await request('/api/strategies/1', { method: 'DELETE' });

    expect(result).toBeUndefined();
  });

  it('parses a ProblemDetail body into an ApiError on a non-ok response', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse(
        { title: 'Not found', status: 404, detail: 'strategy 99 does not exist' },
        404,
      ),
    );

    const error = await request('/api/strategies/99').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe('api');
    expect(error.status).toBe(404);
    expect(error.title).toBe('Not found');
    expect(error.detail).toBe('strategy 99 does not exist');
  });

  it('wraps a transport failure as a network ApiError', async () => {
    globalThis.fetch.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await request('/api/strategies').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe('network');
  });

  it('reports an aborted request as a distinct error kind', async () => {
    globalThis.fetch.mockRejectedValue(new DOMException('The operation was aborted', 'AbortError'));

    const error = await request('/api/strategies', { signal: new AbortController().signal }).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe('aborted');
  });

  it('sends a JSON body with an automatic JSON Content-Type', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ id: 1 }));

    await request('/api/strategies', { method: 'POST', json: { name: 'x' } });

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/strategies');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe(JSON.stringify({ name: 'x' }));
  });

  it('sends a FormData body without setting Content-Type manually', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ versionNumber: 1 }));
    const formData = new FormData();
    formData.append('file', new Blob(['date,open,high,low,close,volume']), 'data.csv');
    formData.append('adjustmentBasis', 'RAW');

    await request('/api/datasets/1/versions', { method: 'POST', formData });

    const [, init] = globalThis.fetch.mock.calls[0];
    expect(init.headers['Content-Type']).toBeUndefined();
    expect(init.body).toBe(formData);
  });

  it('sends a form-urlencoded body for a form option', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ username: 'alice' }));

    await request('/api/auth/login', { method: 'POST', form: { username: 'alice', password: 'hunter2' } });

    const [, init] = globalThis.fetch.mock.calls[0];
    expect(init.headers['Content-Type']).toBe('application/x-www-form-urlencoded');
    expect(init.body).toBe('username=alice&password=hunter2');
  });

  it('sends credentials same-origin on every request', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({}));

    await request('/api/strategies');

    const [, init] = globalThis.fetch.mock.calls[0];
    expect(init.credentials).toBe('same-origin');
  });
});

describe('request - CSRF header (D-39)', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
    clearCookies();
    document.cookie = 'XSRF-TOKEN=the-csrf-token';
  });

  afterEach(() => {
    vi.restoreAllMocks();
    clearCookies();
  });

  it('sends X-XSRF-TOKEN on a POST', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({}));

    await request('/api/strategies', { method: 'POST', json: { name: 'x' } });

    expect(globalThis.fetch.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('the-csrf-token');
  });

  it('sends X-XSRF-TOKEN on a PATCH', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({}));

    await request('/api/strategies/1', { method: 'PATCH', json: { name: 'x' } });

    expect(globalThis.fetch.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('the-csrf-token');
  });

  it('sends X-XSRF-TOKEN on a multipart (FormData) request', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({}));

    await request('/api/datasets/1/versions', { method: 'POST', formData: new FormData() });

    expect(globalThis.fetch.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBe('the-csrf-token');
  });

  it('never sends X-XSRF-TOKEN on a GET', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({}));

    await request('/api/strategies');

    expect(globalThis.fetch.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('omits the header entirely when there is no XSRF-TOKEN cookie yet', async () => {
    clearCookies();
    globalThis.fetch.mockResolvedValue(jsonResponse({}));

    await request('/api/strategies', { method: 'POST', json: {} });

    expect(globalThis.fetch.mock.calls[0][1].headers['X-XSRF-TOKEN']).toBeUndefined();
  });
});

describe('request - unauthorized handling (D-39)', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setUnauthorizedHandler(undefined);
  });

  it('invokes the registered handler on a 401', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    await request('/api/strategies').catch(() => {});

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('never invokes the handler for a non-401 failure', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Not found', status: 404 }, 404));

    await request('/api/strategies/1').catch(() => {});

    expect(handler).not.toHaveBeenCalled();
  });

  it('skips the handler when skipUnauthorizedHandling is set (getMe/login)', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    await request('/api/auth/me', { skipUnauthorizedHandling: true }).catch(() => {});

    expect(handler).not.toHaveBeenCalled();
  });

  it('still throws the ApiError even when the handler is invoked', async () => {
    setUnauthorizedHandler(() => {});
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Unauthorized', status: 401 }, 401));

    const error = await request('/api/strategies').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(401);
  });
});
