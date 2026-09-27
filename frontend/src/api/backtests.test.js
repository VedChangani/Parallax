import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createBacktestRun,
  getBacktestEquity,
  getBacktestRejections,
  getBacktestRun,
  getBacktestTrades,
  listBacktestRuns,
} from './backtests.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const SAMPLE_CREATE_REQUEST = {
  strategyId: 42,
  strategyVersion: 4,
  datasetId: 7,
  datasetVersion: 3,
  config: {
    initialCapital: '100000',
    commissionPerFill: '1',
    slippageRate: '0.0005',
    startDate: '2021-01-01',
    endDate: '2026-09-25',
  },
};

describe('backtests api', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('listBacktestRuns calls GET /api/backtest-runs', async () => {
    await listBacktestRuns();
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/backtest-runs');
    expect(init.method).toBe('GET');
  });

  it('getBacktestRun calls GET /api/backtest-runs/{id}', async () => {
    await getBacktestRun(9);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/9');
  });

  it('getBacktestEquity calls GET /api/backtest-runs/{id}/equity-curve', async () => {
    await getBacktestEquity(9);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/9/equity-curve');
  });

  it('getBacktestTrades calls GET /api/backtest-runs/{id}/trades', async () => {
    await getBacktestTrades(9);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/9/trades');
  });

  it('getBacktestRejections calls GET /api/backtest-runs/{id}/rejections', async () => {
    await getBacktestRejections(9);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/9/rejections');
  });

  it('createBacktestRun posts the exact CreateBacktestRunRequest shape, decimal fields as strings', async () => {
    await createBacktestRun(SAMPLE_CREATE_REQUEST);
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/backtest-runs');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');

    const body = JSON.parse(init.body);
    expect(body).toEqual(SAMPLE_CREATE_REQUEST);
    expect(typeof body.config.initialCapital).toBe('string');
    expect(typeof body.config.commissionPerFill).toBe('string');
    expect(typeof body.config.slippageRate).toBe('string');
  });

  it('createBacktestRun never sends server-controlled fields', async () => {
    await createBacktestRun(SAMPLE_CREATE_REQUEST);
    const body = JSON.parse(globalThis.fetch.mock.calls[0][1].body);
    expect(body).not.toHaveProperty('ownerId');
    expect(body).not.toHaveProperty('id');
    expect(body).not.toHaveProperty('status');
    expect(body).not.toHaveProperty('createdAt');
    expect(body).not.toHaveProperty('engineSemanticsVersion');
    expect(body).not.toHaveProperty('definitionHash');
    expect(body).not.toHaveProperty('contentHash');
  });
});
