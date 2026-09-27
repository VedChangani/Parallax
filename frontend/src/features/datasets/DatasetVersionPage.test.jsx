import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { DatasetVersionPage } from './DatasetVersionPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const sampleDataset = {
  id: 7,
  name: 'Apple daily',
  symbol: 'AAPL',
  latestVersionNumber: 2,
  createdAt: '2024-01-01T00:00:00Z',
};

const sampleVersion = {
  datasetId: 7,
  versionNumber: 2,
  symbol: 'AAPL',
  source: 'CSV_UPLOAD',
  sourceDetail: 'manual.csv',
  adjustmentBasis: 'RAW',
  barCount: 3,
  firstDate: '2024-01-01',
  lastDate: '2024-01-03',
  contentHash: 'c'.repeat(64),
  createdAt: '2024-01-04T00:00:00Z',
};

function stubFetch() {
  globalThis.fetch = vi.fn((url) => {
    if (/\/bars$/.test(url)) {
      return Promise.resolve(
        jsonResponse({
          datasetId: 7,
          versionNumber: 2,
          symbol: 'AAPL',
          contentHash: sampleVersion.contentHash,
          bars: [{ date: '2024-01-01', open: '1.00', high: '2.00', low: '0.50', close: '1.50', volume: 100 }],
        }),
      );
    }
    if (/\/versions\/2$/.test(url)) {
      return Promise.resolve(jsonResponse(sampleVersion));
    }
    return Promise.resolve(jsonResponse(sampleDataset));
  });
}

function renderVersion() {
  return render(
    <MemoryRouter initialEntries={['/datasets/7/versions/2']}>
      <Routes>
        <Route path="/datasets/:id" element={<p>dataset detail page</p>} />
        <Route
          path="/datasets/:id/versions/:version"
          element={<DatasetVersionPage datasetId={7} versionNumber={2} />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DatasetVersionPage', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders every immutable metadata field the backend returns', async () => {
    stubFetch();
    renderVersion();

    expect(await screen.findByRole('heading', { name: 'AAPL' })).toBeTruthy();
    expect(screen.getByText('Immutable')).toBeTruthy();
    expect(screen.getByText('CSV upload')).toBeTruthy();
    expect(screen.getByText('Raw')).toBeTruthy();
    expect(screen.getByText('manual.csv')).toBeTruthy();
    expect(screen.getByText('2024-01-01 → 2024-01-03')).toBeTruthy();
    expect(screen.getByText(sampleVersion.contentHash)).toBeTruthy();
  });

  it('offers a copy button for the content hash', async () => {
    stubFetch();
    renderVersion();

    expect(await screen.findByRole('button', { name: new RegExp(sampleVersion.contentHash) })).toBeTruthy();
  });

  it('navigates back to the dataset detail page', async () => {
    stubFetch();
    renderVersion();

    const link = await screen.findByRole('link', { name: /back to apple daily/i });
    fireEvent.click(link);

    expect(await screen.findByText('dataset detail page')).toBeTruthy();
  });

  it('does NOT call the bars endpoint on mount', async () => {
    stubFetch();
    renderVersion();

    await screen.findByRole('heading', { name: 'AAPL' });
    expect(globalThis.fetch.mock.calls.some(([url]) => /\/bars$/.test(url))).toBe(false);
  });

  it('calls the bars endpoint only once "View bars" is opened', async () => {
    stubFetch();
    renderVersion();

    await screen.findByRole('heading', { name: 'AAPL' });
    fireEvent.click(screen.getByRole('button', { name: /view historical bars/i }));

    expect(await screen.findByText('2024-01-01')).toBeTruthy();
    expect(globalThis.fetch.mock.calls.some(([url]) => /\/bars$/.test(url))).toBe(true);
  });

  it('caches the version metadata under its exact URL as the cache key', async () => {
    stubFetch();
    renderVersion();

    await screen.findByRole('heading', { name: 'AAPL' });
    expect(immutableCache.has('/api/datasets/7/versions/2')).toBe(true);
    expect(immutableCache.get('/api/datasets/7/versions/2')).toEqual(sampleVersion);
  });
});
