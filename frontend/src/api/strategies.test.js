import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createStrategy,
  createStrategyVersion,
  getStrategy,
  getStrategyVersion,
  listStrategies,
  listStrategyVersions,
  updateStrategyMetadata,
} from './strategies.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const SAMPLE_DEFINITION = {
  entryCondition: {
    type: 'compare',
    left: { type: 'indicator', indicator: 'SMA', period: 20 },
    operator: 'GT',
    right: { type: 'indicator', indicator: 'SMA', period: 50 },
  },
  exitCondition: {
    type: 'compare',
    left: { type: 'indicator', indicator: 'RSI', period: 14 },
    operator: 'LT',
    right: { type: 'constant', value: '70' },
  },
  positionSizing: { type: 'cashFraction', fraction: '1' },
};

describe('strategies api', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('listStrategies calls GET /api/strategies', async () => {
    await listStrategies();
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/strategies');
    expect(init.method).toBe('GET');
  });

  it('getStrategy calls GET /api/strategies/{id}', async () => {
    await getStrategy(7);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/strategies/7');
  });

  it('createStrategy posts the exact name/description/definition envelope', async () => {
    await createStrategy({ name: 'Momentum Cross', description: 'SMA trend-following', definition: SAMPLE_DEFINITION });
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/strategies');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({
      name: 'Momentum Cross',
      description: 'SMA trend-following',
      definition: SAMPLE_DEFINITION,
    });
  });

  it('updateStrategyMetadata sends a PATCH with only name/description', async () => {
    await updateStrategyMetadata(7, { name: 'Renamed', description: 'Updated' });
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/strategies/7');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(init.body)).toEqual({ name: 'Renamed', description: 'Updated' });
  });

  it('listStrategyVersions calls GET /api/strategies/{id}/versions', async () => {
    await listStrategyVersions(7);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/strategies/7/versions');
  });

  it('getStrategyVersion calls GET /api/strategies/{id}/versions/{version}', async () => {
    await getStrategyVersion(7, 2);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/strategies/7/versions/2');
  });

  it('createStrategyVersion posts the bare definition tree - never wrapped in an envelope', async () => {
    await createStrategyVersion(7, SAMPLE_DEFINITION);
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/strategies/7/versions');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual(SAMPLE_DEFINITION);
  });
});
