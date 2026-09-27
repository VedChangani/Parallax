import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DatasetDetailPage } from './DatasetDetailPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function stubFetch({ dataset, versions, datasetStatus = 200 }) {
  globalThis.fetch = vi.fn((url) => {
    if (/\/versions$/.test(url)) {
      return Promise.resolve(jsonResponse(versions));
    }
    return Promise.resolve(jsonResponse(dataset, datasetStatus));
  });
}

function renderDetail(datasetId = 7) {
  return render(
    <MemoryRouter initialEntries={[`/datasets/${datasetId}`]}>
      <Routes>
        <Route path="/datasets/:id" element={<DatasetDetailPage datasetId={datasetId} />} />
        <Route path="/datasets/:id/versions/:version" element={<p>data snapshot page</p>} />
        <Route path="/backtests/new" element={<p>new backtest page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

const sampleDataset = {
  id: 7,
  name: 'Apple Inc.',
  symbol: 'AAPL',
  latestVersionNumber: 2,
  createdAt: '2024-01-01T00:00:00Z',
};

const sampleVersions = [
  {
    datasetId: 7,
    versionNumber: 1,
    symbol: 'AAPL',
    source: 'CSV_UPLOAD',
    sourceDetail: 'initial.csv',
    adjustmentBasis: 'RAW',
    barCount: 100,
    firstDate: '2023-01-01',
    lastDate: '2023-06-01',
    contentHash: 'a'.repeat(64),
    createdAt: '2023-06-02T00:00:00Z',
  },
  {
    datasetId: 7,
    versionNumber: 2,
    symbol: 'AAPL',
    source: 'ALPHA_VANTAGE',
    sourceDetail: 'TIME_SERIES_DAILY',
    adjustmentBasis: 'RAW',
    barCount: 250,
    firstDate: '2022-01-01',
    lastDate: '2023-06-01',
    contentHash: 'b'.repeat(64),
    createdAt: '2024-01-01T00:00:00Z',
  },
];

describe('DatasetDetailPage (Market)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state before data arrives', () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {}));
    renderDetail();
    expect(screen.getByText('Loading market…')).toBeTruthy();
  });

  it('presents the current (latest) data snapshot', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'AAPL' })).toBeTruthy();
    expect(screen.getByText(/Apple Inc\. · daily historical data/)).toBeTruthy();
    expect(screen.getByText('Latest data snapshot')).toBeTruthy();
    expect(screen.getByText('Snapshot v2 · immutable')).toBeTruthy();
    expect(screen.getAllByText('2022-01-01 → 2023-06-01').length).toBeGreaterThan(0);
    expect(screen.getAllByText('250').length).toBeGreaterThan(0);
  });

  it('renders the data-snapshots table with the actual backend fields', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    await screen.findByRole('heading', { name: 'AAPL' });
    expect(screen.getByRole('heading', { name: 'Data snapshots (2)' })).toBeTruthy();
    expect(screen.getByText('CSV upload')).toBeTruthy();
    expect(screen.getAllByText('Alpha Vantage').length).toBeGreaterThan(0);
    expect(screen.getByText('2023-01-01 → 2023-06-01')).toBeTruthy();
  });

  it('shows an empty state when a market has no data snapshots yet', async () => {
    stubFetch({ dataset: { ...sampleDataset, latestVersionNumber: 0 }, versions: [] });
    renderDetail();

    expect(await screen.findByText('No data snapshots yet')).toBeTruthy();
    expect(screen.getByText('No data snapshot yet. Load historical data below to begin.')).toBeTruthy();
  });

  it('shows an error state when the market does not exist (404)', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse({ title: 'Not found', detail: 'dataset 7 does not exist' }, 404));
    renderDetail();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('offers "Create backtest" navigation to /backtests/new', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    const link = await screen.findByRole('link', { name: 'Create backtest' });
    fireEvent.click(link);

    expect(await screen.findByText('new backtest page')).toBeTruthy();
  });

  it('navigates from the latest-snapshot panel to the snapshot detail page', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    const link = await screen.findByRole('link', { name: 'Open snapshot' });
    fireEvent.click(link);

    expect(await screen.findByText('data snapshot page')).toBeTruthy();
  });

  it('navigates from the data-snapshots table to a specific snapshot', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    await screen.findByRole('heading', { name: 'AAPL' });
    const links = screen.getAllByRole('link', { name: /open snapshot/i });
    fireEvent.click(links[links.length - 1]);

    expect(await screen.findByText('data snapshot page')).toBeTruthy();
  });

  it('keeps CSV import available as a secondary, collapsed "Advanced data import" section', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    await screen.findByRole('heading', { name: 'AAPL' });
    expect(screen.getByText('Advanced data import - custom CSV')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Load historical data' })).toBeTruthy();
  });
});
