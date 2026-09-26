import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AppRoutes } from './routes.jsx';

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

function jsonResponse(body) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
}

/** Routes to real Dataset endpoint shapes so /datasets/* pages resolve deterministically. */
function stubDatasetFetch() {
  globalThis.fetch = vi.fn((url) => {
    if (/\/api\/datasets\/7\/versions\/2$/.test(url)) {
      return Promise.resolve(
        jsonResponse({
          datasetId: 7,
          versionNumber: 2,
          symbol: 'AAPL',
          source: 'CSV_UPLOAD',
          sourceDetail: 'manual.csv',
          adjustmentBasis: 'RAW',
          barCount: 10,
          firstDate: '2024-01-01',
          lastDate: '2024-01-10',
          contentHash: 'a'.repeat(64),
          createdAt: '2024-01-10T00:00:00Z',
        }),
      );
    }
    if (/\/api\/datasets\/7\/versions$/.test(url)) {
      return Promise.resolve(jsonResponse([]));
    }
    if (/\/api\/datasets\/7$/.test(url)) {
      return Promise.resolve(
        jsonResponse({ id: 7, name: 'Apple daily', symbol: 'AAPL', latestVersionNumber: 2, createdAt: '2024-01-01T00:00:00Z' }),
      );
    }
    return Promise.resolve(jsonResponse([]));
  });
}

describe('AppRoutes', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('redirects the root route to /backtests', () => {
    renderAt('/');
    expect(screen.getByRole('heading', { name: 'Backtests' })).toBeTruthy();
  });

  it('renders the strategies list placeholder', () => {
    renderAt('/strategies');
    expect(screen.getByRole('heading', { name: 'Strategies' })).toBeTruthy();
  });

  it('renders the new-strategy placeholder', () => {
    renderAt('/strategies/new');
    expect(screen.getByRole('heading', { name: 'New strategy' })).toBeTruthy();
  });

  it('renders the strategy detail placeholder using the route param', () => {
    renderAt('/strategies/42');
    expect(screen.getByRole('heading', { name: 'Strategy #42' })).toBeTruthy();
  });

  it('renders the strategy version placeholders using route params', () => {
    renderAt('/strategies/42/versions/new');
    expect(screen.getByRole('heading', { name: 'New version for strategy #42' })).toBeTruthy();

    renderAt('/strategies/42/versions/3');
    expect(screen.getByRole('heading', { name: 'Strategy #42 — version 3' })).toBeTruthy();
  });

  it('renders the datasets list route', () => {
    stubDatasetFetch();
    renderAt('/datasets');
    expect(screen.getByRole('heading', { name: 'Datasets' })).toBeTruthy();
  });

  it('renders the dataset detail route using the route param', async () => {
    stubDatasetFetch();
    renderAt('/datasets/7');
    expect(await screen.findByRole('heading', { name: 'Apple daily' })).toBeTruthy();
  });

  it('renders the dataset version route using both route params', async () => {
    stubDatasetFetch();
    renderAt('/datasets/7/versions/2');
    expect(await screen.findByRole('heading', { name: 'AAPL — version 2' })).toBeTruthy();
  });

  it('renders the new-backtest placeholder', () => {
    renderAt('/backtests/new');
    expect(screen.getByRole('heading', { name: 'New backtest run' })).toBeTruthy();
  });

  it('renders the backtest run layout with its Overview tab active by default', () => {
    renderAt('/backtests/7');
    expect(screen.getByRole('heading', { name: 'Backtest run #7' })).toBeTruthy();
    expect(screen.getByText('Run overview coming soon')).toBeTruthy();
  });

  it('renders the trades tab content under the same run layout', () => {
    renderAt('/backtests/7/trades');
    expect(screen.getByRole('heading', { name: 'Backtest run #7' })).toBeTruthy();
    expect(screen.getByText('Trades coming soon')).toBeTruthy();
  });

  it('renders the rejections tab content under the same run layout', () => {
    renderAt('/backtests/7/rejections');
    expect(screen.getByRole('heading', { name: 'Backtest run #7' })).toBeTruthy();
    expect(screen.getByText('Rejections coming soon')).toBeTruthy();
  });

  it('renders NotFound for an unrecognized route', () => {
    renderAt('/this-route-does-not-exist');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeTruthy();
  });
});
