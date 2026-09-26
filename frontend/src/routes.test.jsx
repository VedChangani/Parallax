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
    right: { type: 'constant', value: '30' },
  },
  positionSizing: { type: 'cashFraction', fraction: '1' },
};

/** Routes to real Strategy endpoint shapes so /strategies/* pages resolve deterministically. */
function stubStrategyFetch() {
  globalThis.fetch = vi.fn((url) => {
    if (/\/api\/strategies\/42\/versions\/3$/.test(url)) {
      return Promise.resolve(
        jsonResponse({
          strategyId: 42,
          versionNumber: 3,
          schemaVersion: 1,
          definitionHash: 'b'.repeat(64),
          createdAt: '2024-01-10T00:00:00Z',
          definition: SAMPLE_DEFINITION,
        }),
      );
    }
    if (/\/api\/strategies\/42\/versions$/.test(url)) {
      return Promise.resolve(
        jsonResponse([
          { strategyId: 42, versionNumber: 3, schemaVersion: 1, definitionHash: 'b'.repeat(64), createdAt: '2024-01-10T00:00:00Z' },
        ]),
      );
    }
    if (/\/api\/strategies\/42$/.test(url)) {
      return Promise.resolve(
        jsonResponse({ id: 42, name: 'Momentum Cross', description: 'SMA trend-following', latestVersionNumber: 3, createdAt: '2024-01-01T00:00:00Z' }),
      );
    }
    return Promise.resolve(jsonResponse([]));
  });
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

  it('renders the strategies list route', () => {
    stubStrategyFetch();
    renderAt('/strategies');
    expect(screen.getByRole('heading', { name: 'Strategies' })).toBeTruthy();
  });

  it('renders the new-strategy route', () => {
    renderAt('/strategies/new');
    expect(screen.getByRole('heading', { name: 'New strategy' })).toBeTruthy();
  });

  it('renders the strategy detail route using the route param', async () => {
    stubStrategyFetch();
    renderAt('/strategies/42');
    expect(await screen.findByRole('heading', { name: 'Momentum Cross' })).toBeTruthy();
  });

  it('renders the strategy version routes using route params', async () => {
    stubStrategyFetch();
    renderAt('/strategies/42/versions/3');
    expect(await screen.findByRole('heading', { name: 'Momentum Cross — v3' })).toBeTruthy();
  });

  it('renders the new-version route, seeded from the latest version', async () => {
    stubStrategyFetch();
    renderAt('/strategies/42/versions/new');
    expect(await screen.findByRole('heading', { name: 'New version — Momentum Cross' })).toBeTruthy();
  });

  it('renders the markets list route', () => {
    stubDatasetFetch();
    renderAt('/datasets');
    expect(screen.getByRole('heading', { name: 'Markets' })).toBeTruthy();
  });

  it('renders the market detail route using the route param', async () => {
    stubDatasetFetch();
    renderAt('/datasets/7');
    expect(await screen.findByRole('heading', { name: 'AAPL' })).toBeTruthy();
  });

  it('renders the data snapshot route using both route params', async () => {
    stubDatasetFetch();
    renderAt('/datasets/7/versions/2');
    expect(await screen.findByRole('heading', { name: 'AAPL' })).toBeTruthy();
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
