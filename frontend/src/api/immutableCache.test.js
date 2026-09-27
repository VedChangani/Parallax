import { beforeEach, describe, expect, it } from 'vitest';
import * as immutableCache from './immutableCache.js';

describe('immutableCache', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  it('reports a cache miss', () => {
    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    expect(immutableCache.get('/api/strategies/1/versions/1')).toBeUndefined();
  });

  it('stores and retrieves a value by key', () => {
    immutableCache.set('/api/strategies/1/versions/1', { versionNumber: 1 });

    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(true);
    expect(immutableCache.get('/api/strategies/1/versions/1')).toEqual({ versionNumber: 1 });
  });

  it('clears every stored entry', () => {
    immutableCache.set('/api/strategies/1/versions/1', 1);
    immutableCache.set('/api/datasets/1/versions/1', 2);

    immutableCache.clear();

    expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    expect(immutableCache.has('/api/datasets/1/versions/1')).toBe(false);
  });

  it('never expires an entry on its own', async () => {
    immutableCache.set('/api/datasets/1/versions/1', { versionNumber: 1 });

    await new Promise((resolve) => setTimeout(resolve, 10));

    expect(immutableCache.has('/api/datasets/1/versions/1')).toBe(true);
  });
});
