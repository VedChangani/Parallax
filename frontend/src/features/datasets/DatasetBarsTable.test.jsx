import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { DatasetBarsTable } from './DatasetBarsTable.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function makeBars(count) {
  const start = new Date('2024-01-01T00:00:00Z');
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(start);
    date.setUTCDate(date.getUTCDate() + index);
    return {
      date: date.toISOString().slice(0, 10),
      open: '100.123456789',
      high: '101.00',
      low: '99.00',
      close: '100.50',
      volume: 1000000 + index,
    };
  });
}

function barsResponse(bars) {
  return jsonResponse({ datasetId: 7, versionNumber: 2, symbol: 'AAPL', contentHash: 'x', bars });
}

describe('DatasetBarsTable', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state, then the fetched bars', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(barsResponse(makeBars(3)));
    render(<DatasetBarsTable datasetId={7} versionNumber={2} />);

    expect(screen.getByText('Loading bars…')).toBeTruthy();
    expect(await screen.findAllByRole('row')).toHaveLength(4);
  });

  it('preserves exact OHLC strings, never reformatting/re-parsing them', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(barsResponse(makeBars(1)));
    render(<DatasetBarsTable datasetId={7} versionNumber={2} />);

    expect(await screen.findByText('100.123456789')).toBeTruthy();
  });

  it('paginates client-side at 100 rows per page and never mounts every row at once', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(barsResponse(makeBars(250)));
    render(<DatasetBarsTable datasetId={7} versionNumber={2} />);

    const firstPageRows = await screen.findAllByRole('row');
    expect(firstPageRows).toHaveLength(101);
    expect(screen.getByText('Page 1 of 3 · 250 bars')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Page 2 of 3 · 250 bars')).toBeTruthy();
    expect(screen.getAllByRole('row')).toHaveLength(101);

    fireEvent.click(screen.getByRole('button', { name: 'Last' }));
    expect(await screen.findByText('Page 3 of 3 · 250 bars')).toBeTruthy();
    expect(screen.getAllByRole('row')).toHaveLength(51);
    expect(screen.getByRole('button', { name: 'Next' }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'First' }));
    expect(await screen.findByText('Page 1 of 3 · 250 bars')).toBeTruthy();
  });

  it('shows an error state on failure', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500));
    render(<DatasetBarsTable datasetId={7} versionNumber={2} />);

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('caches the bars response under its exact URL as the cache key', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(barsResponse(makeBars(2)));
    render(<DatasetBarsTable datasetId={7} versionNumber={2} />);

    await screen.findAllByRole('row');
    expect(immutableCache.has('/api/datasets/7/versions/2/bars')).toBe(true);
  });
});
