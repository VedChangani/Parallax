import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BacktestRunLayout } from './BacktestRunLayout.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const RUN = {
  id: 7, strategyId: 1, strategyVersion: 1, definitionHash: 'a'.repeat(64), datasetId: 1, datasetVersion: 1,
  contentHash: 'b'.repeat(64), engineSemanticsVersion: 1, initialCapital: '100000', commissionPerFill: '1',
  slippageRate: '0.0005', startDate: '2024-01-01', endDate: '2024-03-22', firstEvaluableDate: '2024-01-01',
  totalCommission: '0', totalSlippageCost: '0',
  metrics: {
    totalReturn: 0, cagr: null, volatility: null, sharpeRatio: null, maxDrawdown: 0, closedTradeCount: 0,
    winRate: null, averageWin: null, averageLoss: null,
  },
  benchmark: { cash: '100000', quantity: 0, costBasis: '0', totalReturn: 0 },
  createdAt: '2024-03-22T00:00:00Z',
};

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/backtests/:runId" element={<BacktestRunLayout />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('BacktestRunLayout (N6)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  // Empty/whitespace-only segments are excluded here: react-router itself
  // never matches an empty ":runId" segment against this route at all (a
  // router-level "no match", handled by the app's own top-level "*" ->
  // NotFound route, not this layout) - not a meaningful case for this
  // isolated single-route test tree.
  it.each(['not-a-number', 'NaN', '1.5', '-1', '0', '01'])(
    'renders a clean not-found state and makes no API request for an invalid runId (%s)',
    async (invalidRunId) => {
      globalThis.fetch = vi.fn(() => Promise.reject(new Error('fetch must never be called for an invalid runId')));
      renderAt(`/backtests/${encodeURIComponent(invalidRunId)}`);

      expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeTruthy();
      expect(globalThis.fetch).not.toHaveBeenCalled();
    },
  );

  it('still behaves normally for a valid numeric runId', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/api\/backtest-runs\/7\/equity-curve$/.test(url)) return Promise.resolve(jsonResponse([]));
      if (/\/api\/backtest-runs\/7$/.test(url)) return Promise.resolve(jsonResponse(RUN));
      if (/\/api\/strategies\/1$/.test(url)) return Promise.resolve(jsonResponse({ id: 1, name: 'S', description: '', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' }));
      if (/\/api\/datasets\/1\/versions\/1$/.test(url)) {
        return Promise.resolve(
          jsonResponse({
            datasetId: 1, versionNumber: 1, symbol: 'AAA', source: 'CSV_UPLOAD', sourceDetail: 'x.csv',
            adjustmentBasis: 'RAW', barCount: 1, firstDate: '2024-01-01', lastDate: '2024-03-22',
            contentHash: 'b'.repeat(64), createdAt: '2024-01-01T00:00:00Z',
          }),
        );
      }
      if (/\/api\/datasets\/1$/.test(url)) return Promise.resolve(jsonResponse({ id: 1, name: 'D', symbol: 'AAA', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' }));
      return Promise.resolve(jsonResponse([]));
    });
    renderAt('/backtests/7');

    expect(await screen.findByRole('heading', { name: 'Backtest #7' })).toBeTruthy();
    expect(globalThis.fetch.mock.calls.some(([url]) => /\/api\/backtest-runs\/7$/.test(url))).toBe(true);
  });
});
