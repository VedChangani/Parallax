import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getBacktestEquityCsv, getBacktestTradesCsv } from './backtests.js';

function csvResponse(text, status = 200) {
  return new Response(text, { status, headers: { 'content-type': 'text/csv;charset=UTF-8' } });
}

describe('backtest CSV api', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requests the equity-curve CSV and returns the body exactly as received', async () => {
    const body = 'date,equity\r\n2024-01-02,10000\r\n';
    globalThis.fetch.mockResolvedValue(csvResponse(body));

    await expect(getBacktestEquityCsv(7)).resolves.toBe(body);

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/backtest-runs/7/equity-curve.csv');
    expect(init.method).toBe('GET');
    expect(init.credentials).toBe('same-origin');
  });

  it('requests the trades CSV', async () => {
    globalThis.fetch.mockResolvedValue(csvResponse('status\r\n'));

    await expect(getBacktestTradesCsv(7)).resolves.toBe('status\r\n');
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/7/trades.csv');
  });

  it('returns an empty successful body as an empty string', async () => {
    globalThis.fetch.mockResolvedValue(csvResponse(''));

    await expect(getBacktestTradesCsv(7)).resolves.toBe('');
  });

  it('still maps an error response to an ApiError', async () => {
    globalThis.fetch.mockResolvedValue(
      new Response(JSON.stringify({ title: 'Not found', detail: 'no such run' }), {
        status: 404,
        headers: { 'content-type': 'application/problem+json' },
      }),
    );

    await expect(getBacktestEquityCsv(7)).rejects.toMatchObject({ status: 404 });
  });
});
