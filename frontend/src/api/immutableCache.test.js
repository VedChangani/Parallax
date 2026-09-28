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

  describe('epoch (D-39)', () => {
    it('accepts a write from the current epoch', () => {
      const epoch = immutableCache.currentEpoch();

      immutableCache.set('/api/strategies/1/versions/1', { versionNumber: 1 }, epoch);

      expect(immutableCache.get('/api/strategies/1/versions/1')).toEqual({ versionNumber: 1 });
    });

    it('drops a write captured from a since-superseded epoch (a stale response after a login/logout/401)', () => {
      const staleEpoch = immutableCache.currentEpoch();
      immutableCache.bumpEpoch(); // simulates a login/logout/401 that happened while the fetch was in flight

      immutableCache.set('/api/strategies/1/versions/1', { versionNumber: 1 }, staleEpoch);

      expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
    });

    it('bumpEpoch also clears every existing entry', () => {
      immutableCache.set('/api/strategies/1/versions/1', 1);
      immutableCache.set('/api/datasets/1/versions/1', 2);

      immutableCache.bumpEpoch();

      expect(immutableCache.has('/api/strategies/1/versions/1')).toBe(false);
      expect(immutableCache.has('/api/datasets/1/versions/1')).toBe(false);
    });

    it('advances the epoch by exactly one per call', () => {
      const before = immutableCache.currentEpoch();
      immutableCache.bumpEpoch();
      expect(immutableCache.currentEpoch()).toBe(before + 1);
    });
  });
});
