import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BacktestHistoryPage } from './BacktestHistoryPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

// IDs are deliberately NOT already in "newest run has the highest id, next
// call site returns them pre-sorted" order, so a passing "sorted
// newest-first by id" test proves the page itself sorts descending by id -
// it does not just pass through whatever array order the backend sent.
// createdAt is likewise deliberately non-monotonic with id, so a test that
// still gets id-descending order proves sorting uses id, not a createdAt
// string comparison (N1: id is exact and format-independent; a timestamp
// string comparison is not).
const RUN_A = {
  id: 101, strategyId: 501, strategyVersion: 2, definitionHash: 'a'.repeat(64),
  datasetId: 701, datasetVersion: 3, contentHash: 'b'.repeat(64), engineSemanticsVersion: 1,
  startDate: '2024-01-01', endDate: '2024-06-01', totalReturn: 0.1532, benchmarkTotalReturn: 0.0604,
  createdAt: '2024-06-01T00:00:00Z',
};
const RUN_B = {
  id: 102, strategyId: 502, strategyVersion: 1, definitionHash: 'c'.repeat(64),
  datasetId: 702, datasetVersion: 1, contentHash: 'd'.repeat(64), engineSemanticsVersion: 1,
  startDate: '2024-02-01', endDate: '2024-05-01', totalReturn: -0.042, benchmarkTotalReturn: 0.01,
  createdAt: '2024-01-01T00:00:00Z',
};
const RUN_C = {
  id: 103, strategyId: 501, strategyVersion: 3, definitionHash: 'e'.repeat(64),
  datasetId: 701, datasetVersion: 4, contentHash: 'f'.repeat(64), engineSemanticsVersion: 2,
  startDate: '2024-03-01', endDate: '2024-04-01', totalReturn: 0, benchmarkTotalReturn: 0,
  createdAt: '2024-03-10T00:00:00Z',
};

const MARKET_701 = { id: 701, name: 'Reliance Industries', symbol: 'RELIANCE', latestVersionNumber: 4, createdAt: '2024-01-01T00:00:00Z' };
const STRATEGY_501 = { id: 501, name: 'Momentum Cross', description: '', latestVersionNumber: 3, createdAt: '2024-01-01T00:00:00Z' };

/**
 * @param {object} [overrides]
 * @param {object[]} [overrides.runs]
 * @param {object[]} [overrides.datasets] - defaults to an empty list, so a
 *   test that doesn't care about names sees only the honest `#id` fallback.
 * @param {object[]} [overrides.strategies]
 */
function mockFetch({ runs = [RUN_A, RUN_B, RUN_C], datasets = [], strategies = [] } = {}) {
  return vi.fn((url) => {
    if (url === '/api/backtest-runs') return Promise.resolve(jsonResponse(runs));
    if (url === '/api/datasets') return Promise.resolve(jsonResponse(datasets));
    if (url === '/api/strategies') return Promise.resolve(jsonResponse(strategies));
    throw new Error(`unhandled request: ${url}`);
  });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/backtests']}>
      <Routes>
        <Route path="/backtests" element={<BacktestHistoryPage />} />
        <Route path="/backtests/new" element={<p>new backtest page</p>} />
        <Route path="/backtests/:runId" element={<p>run detail page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('BacktestHistoryPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading skeleton before the list arrives', () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {}));
    renderPage();
    expect(screen.getByText('Loading backtests…')).toBeTruthy();
  });

  it('lists real runs sorted newest-first by run id, not backend array order or createdAt', async () => {
    globalThis.fetch = mockFetch();
    renderPage();

    const rows = await screen.findAllByRole('row');
    const dataRows = rows.slice(1); // skip the header row
    expect(within(dataRows[0]).getByText('#103')).toBeTruthy(); // highest id
    expect(within(dataRows[1]).getByText('#102')).toBeTruthy();
    expect(within(dataRows[2]).getByText('#101')).toBeTruthy(); // lowest id
  });

  it('makes one request per resource type - no per-row detail fetch (no N+1)', async () => {
    globalThis.fetch = mockFetch();
    renderPage();
    await screen.findByText('#101');

    // Three runs rendered, but exactly one call each for runs/datasets/strategies -
    // proving the request count scales with resource *types*, not row count.
    expect(globalThis.fetch).toHaveBeenCalledTimes(3);
    const urls = globalThis.fetch.mock.calls.map(([url]) => url).sort();
    expect(urls).toEqual(['/api/backtest-runs', '/api/datasets', '/api/strategies']);
  });

  it('resolves a known market/strategy to its current name, alongside its version reference', async () => {
    globalThis.fetch = mockFetch({ runs: [RUN_A], datasets: [MARKET_701], strategies: [STRATEGY_501] });
    renderPage();

    const row = (await screen.findByText('#101')).closest('tr');
    expect(within(row).getByText('RELIANCE')).toBeTruthy();
    expect(within(row).getByText('Momentum Cross')).toBeTruthy();
    expect(within(row).getByText('Snapshot v3')).toBeTruthy();
    expect(within(row).getByText('Version v2')).toBeTruthy();
  });

  it('falls back to the honest id reference when a market/strategy is not (yet) known - never a fabricated name', async () => {
    globalThis.fetch = mockFetch({ runs: [RUN_A] }); // no datasets/strategies list entries
    renderPage();

    const row = (await screen.findByText('#101')).closest('tr');
    expect(within(row).getByText('Market #701')).toBeTruthy();
    expect(within(row).getByText('Strategy #501')).toBeTruthy();

    fireEvent.click(within(row).getByText('Detail'));
    expect(within(row).getByText('engine v1')).toBeTruthy();
    expect(within(row).getByText(/strategy a{12}…/)).toBeTruthy();
    expect(within(row).getByText(/dataset b{12}…/)).toBeTruthy();
  });

  it('shows the run-level date range and returns straight from the list response (I8)', async () => {
    globalThis.fetch = mockFetch({ runs: [RUN_A] });
    renderPage();

    const row = (await screen.findByText('#101')).closest('tr');
    expect(within(row).getByText('2024-01-01 → 2024-06-01')).toBeTruthy();
    expect(within(row).getByText('+15.32%')).toBeTruthy(); // strategy totalReturn
    expect(within(row).getByText(/B&H \+6\.04%/)).toBeTruthy(); // benchmarkTotalReturn
  });

  it('never shows a metric absent from the list response, such as CAGR or Sharpe', async () => {
    globalThis.fetch = mockFetch({ runs: [RUN_A] });
    renderPage();
    await screen.findByText('#101');

    expect(screen.queryByText(/CAGR|Sharpe/)).toBeNull();
  });

  it('shows an empty state with a primary action when there are no runs', async () => {
    globalThis.fetch = mockFetch({ runs: [] });
    renderPage();

    expect(await screen.findByText('No backtests yet')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: 'New backtest' }).length).toBeGreaterThan(0);
  });

  it('shows a safe generic error with retry, and reload succeeds', async () => {
    let runsCallCount = 0;
    globalThis.fetch = vi.fn((url) => {
      if (url === '/api/backtest-runs') {
        runsCallCount += 1;
        return Promise.resolve(
          runsCallCount === 1
            ? jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500)
            : jsonResponse([RUN_A]),
        );
      }
      if (url === '/api/datasets') return Promise.resolve(jsonResponse([]));
      if (url === '/api/strategies') return Promise.resolve(jsonResponse([]));
      throw new Error(`unhandled request: ${url}`);
    });
    renderPage();

    expect(await screen.findByText('Could not load backtests')).toBeTruthy();
    expect(screen.getByText('an internal error occurred')).toBeTruthy();
    expect(screen.queryByText(/Exception|stack trace/i)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('#101')).toBeTruthy();
  });

  describe('search', () => {
    it('filters by run ID', async () => {
      globalThis.fetch = mockFetch();
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '102' } });
      expect(screen.getByText('#102')).toBeTruthy();
      expect(screen.queryByText('#101')).toBeNull();
      expect(screen.queryByText('#103')).toBeNull();
    });

    it('filters by strategy ID, matching every run that shares it', async () => {
      globalThis.fetch = mockFetch();
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '501' } });
      expect(screen.getByText('#101')).toBeTruthy();
      expect(screen.getByText('#103')).toBeTruthy();
      expect(screen.queryByText('#102')).toBeNull();
    });

    it('filters by dataset ID', async () => {
      globalThis.fetch = mockFetch();
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '702' } });
      expect(screen.getByText('#102')).toBeTruthy();
      expect(screen.queryByText('#101')).toBeNull();
      expect(screen.queryByText('#103')).toBeNull();
    });

    it('filters by dataset version', async () => {
      globalThis.fetch = mockFetch();
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '4' } });
      expect(screen.getByText('#103')).toBeTruthy();
      expect(screen.queryByText('#101')).toBeNull();
      expect(screen.queryByText('#102')).toBeNull();
    });

    it('shows a no-match message for a search that matches nothing, without treating it as an error', async () => {
      globalThis.fetch = mockFetch();
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '999999' } });
      expect(screen.getByText('No runs match your search.')).toBeTruthy();
      expect(screen.queryByRole('alert')).toBeNull();
    });
  });

  describe('navigation', () => {
    it('links "New backtest" to /backtests/new', async () => {
      globalThis.fetch = mockFetch({ runs: [RUN_A] });
      renderPage();
      await screen.findByText('#101');

      fireEvent.click(screen.getAllByRole('link', { name: 'New backtest' })[0]);
      expect(await screen.findByText('new backtest page')).toBeTruthy();
    });

    it('opens a run at /backtests/{runId}', async () => {
      globalThis.fetch = mockFetch({ runs: [RUN_A] });
      renderPage();
      await screen.findByText('#101');

      fireEvent.click(screen.getByRole('link', { name: 'Open run →' }));
      expect(await screen.findByText('run detail page')).toBeTruthy();
    });

    it('also opens a run by clicking its run number', async () => {
      globalThis.fetch = mockFetch({ runs: [RUN_A] });
      renderPage();

      fireEvent.click(await screen.findByText('#101'));
      expect(await screen.findByText('run detail page')).toBeTruthy();
    });
  });

  it('never shows an edit, delete, or re-run control - a run is an immutable historical record', async () => {
    globalThis.fetch = mockFetch({ runs: [RUN_A] });
    renderPage();
    await screen.findByText('#101');

    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('link', { name: /re-?run/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /re-?run/i })).toBeNull();
  });

  describe('accessibility', () => {
    it('gives the search input an accessible label', async () => {
      globalThis.fetch = mockFetch({ runs: [RUN_A] });
      renderPage();
      expect(await screen.findByLabelText('Search backtests')).toBeTruthy();
    });

    it('gives the table real column headers, including the new date-range and return columns', async () => {
      globalThis.fetch = mockFetch({ runs: [RUN_A] });
      renderPage();
      await screen.findByText('#101');

      expect(screen.getByRole('columnheader', { name: 'Run' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Market' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Strategy' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Period' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Return' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Created' })).toBeTruthy();
    });
  });
});
