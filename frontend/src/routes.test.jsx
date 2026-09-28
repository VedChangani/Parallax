import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './auth/AuthProvider.jsx';
import { AppRoutes } from './routes.jsx';

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function jsonResponse(body) {
  return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
}

/** D-39: every protected route now needs GET /api/auth/me to resolve before it renders. */
function authenticatedMeResponse(url) {
  return /\/api\/auth\/me$/.test(url) ? Promise.resolve(jsonResponse({ username: 'test-user' })) : undefined;
}

/** For a test that fetches nothing else - resolves identity, everything else returns an empty list. */
function stubAuthenticatedOnlyFetch() {
  globalThis.fetch = vi.fn((url) => authenticatedMeResponse(url) ?? Promise.resolve(jsonResponse([])));
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
    const me = authenticatedMeResponse(url);
    if (me) return me;
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
    const me = authenticatedMeResponse(url);
    if (me) return me;
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

const SAMPLE_METRICS = {
  totalReturn: 0.12,
  cagr: null,
  volatility: null,
  sharpeRatio: null,
  maxDrawdown: 0.05,
  closedTradeCount: 0,
  winRate: null,
  averageWin: null,
  averageLoss: null,
};

/** Routes to real backtest-run endpoint shapes so /backtests/7/* pages resolve deterministically. */
function stubBacktestRunFetch() {
  globalThis.fetch = vi.fn((url) => {
    const me = authenticatedMeResponse(url);
    if (me) return me;
    if (/\/api\/backtest-runs\/7\/equity-curve$/.test(url)) return Promise.resolve(jsonResponse([]));
    if (/\/api\/backtest-runs\/7\/trades$/.test(url)) return Promise.resolve(jsonResponse([]));
    if (/\/api\/backtest-runs\/7\/rejections$/.test(url)) return Promise.resolve(jsonResponse([]));
    if (/\/api\/backtest-runs\/7$/.test(url)) {
      return Promise.resolve(
        jsonResponse({
          id: 7,
          strategyId: 1,
          strategyVersion: 1,
          definitionHash: 'a'.repeat(64),
          datasetId: 1,
          datasetVersion: 1,
          contentHash: 'b'.repeat(64),
          engineSemanticsVersion: 1,
          initialCapital: '100000',
          commissionPerFill: '1',
          slippageRate: '0.0005',
          startDate: '2024-01-01',
          endDate: '2024-03-22',
          firstEvaluableDate: '2024-03-08',
          totalCommission: '0',
          totalSlippageCost: '0',
          metrics: SAMPLE_METRICS,
          benchmark: { cash: '49.05', quantity: 999, costBasis: '99950.95', totalReturn: 0.6 },
          createdAt: '2024-03-22T00:00:00Z',
        }),
      );
    }
    if (/\/api\/strategies\/1$/.test(url)) {
      return Promise.resolve(
        jsonResponse({ id: 1, name: 'Momentum Cross', description: '', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' }),
      );
    }
    if (/\/api\/datasets\/1$/.test(url)) {
      return Promise.resolve(
        jsonResponse({ id: 1, name: 'Reliance Industries', symbol: 'RELIANCE', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' }),
      );
    }
    return Promise.resolve(jsonResponse([]));
  });
}

describe('AppRoutes', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('redirects the root route to /backtests', async () => {
    stubAuthenticatedOnlyFetch();
    renderAt('/');
    expect(await screen.findByRole('heading', { name: 'Backtests' })).toBeTruthy();
  });

  it('renders the strategies list route', async () => {
    stubStrategyFetch();
    renderAt('/strategies');
    expect(await screen.findByRole('heading', { name: 'Strategies' })).toBeTruthy();
  });

  it('renders the new-strategy route', async () => {
    stubAuthenticatedOnlyFetch();
    renderAt('/strategies/new');
    expect(await screen.findByRole('heading', { name: 'New strategy' })).toBeTruthy();
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

  it('renders the markets list route', async () => {
    stubDatasetFetch();
    renderAt('/datasets');
    expect(await screen.findByRole('heading', { name: 'Markets' })).toBeTruthy();
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

  it('renders the new-backtest page', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([])));
    renderAt('/backtests/new');
    expect(await screen.findByRole('heading', { name: 'New backtest' })).toBeTruthy();
  });

  it('renders the backtest run layout with its Overview tab active by default', async () => {
    stubBacktestRunFetch();
    renderAt('/backtests/7');
    expect(await screen.findByRole('heading', { name: 'Backtest #7' })).toBeTruthy();
    expect(screen.getByText('Performance')).toBeTruthy();
  });

  it('renders the trades tab content under the same run layout', async () => {
    stubBacktestRunFetch();
    renderAt('/backtests/7/trades');
    expect(await screen.findByRole('heading', { name: 'Backtest #7' })).toBeTruthy();
    expect(await screen.findByText('No trades were generated by this run.')).toBeTruthy();
  });

  it('renders the rejections tab content under the same run layout', async () => {
    stubBacktestRunFetch();
    renderAt('/backtests/7/rejections');
    expect(await screen.findByRole('heading', { name: 'Backtest #7' })).toBeTruthy();
    expect(await screen.findByText('No orders were rejected.')).toBeTruthy();
  });

  it('renders NotFound for an unrecognized route', () => {
    renderAt('/this-route-does-not-exist');
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeTruthy();
  });
});
