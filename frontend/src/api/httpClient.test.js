import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './apiError.js';
import { request } from './httpClient.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
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
});
