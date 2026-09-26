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
        <Route path="/datasets/:id/versions/:version" element={<p>dataset version page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

const sampleDataset = {
  id: 7,
  name: 'Apple daily',
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

describe('DatasetDetailPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state before data arrives', () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {}));
    renderDetail();
    expect(screen.getByText('Loading dataset…')).toBeTruthy();
  });

  it('renders dataset metadata once loaded', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Apple daily' })).toBeTruthy();
    expect(screen.getAllByText('AAPL').length).toBeGreaterThan(0);
    expect(screen.getAllByText('v2').length).toBeGreaterThan(0);
    expect(screen.getByText('2', { selector: 'dd' })).toBeTruthy();
  });

  it('renders the version history table with the actual backend fields', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    await screen.findByRole('heading', { name: 'Apple daily' });
    expect(screen.getByRole('link', { name: 'v2' })).toBeTruthy();
    expect(screen.getByText('CSV upload')).toBeTruthy();
    expect(screen.getByText('Alpha Vantage')).toBeTruthy();
    expect(screen.getByText('initial.csv')).toBeTruthy();
    expect(screen.getByText('2023-01-01')).toBeTruthy();
  });

  it('shows an empty state when a dataset has no versions yet', async () => {
    stubFetch({ dataset: { ...sampleDataset, latestVersionNumber: 0 }, versions: [] });
    renderDetail();

    expect(await screen.findByText('No versions yet')).toBeTruthy();
  });

  it('shows an error state when the dataset does not exist (404)', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValue(jsonResponse({ title: 'Not found', detail: 'dataset 7 does not exist' }, 404));
    renderDetail();

    expect(await screen.findByRole('alert')).toBeTruthy();
  });

  it('navigates from version history to the version detail page', async () => {
    stubFetch({ dataset: sampleDataset, versions: sampleVersions });
    renderDetail();

    const link = await screen.findByRole('link', { name: 'v2' });
    fireEvent.click(link);

    expect(await screen.findByText('dataset version page')).toBeTruthy();
  });
});
