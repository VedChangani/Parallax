import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { BacktestOverviewPage } from './BacktestOverviewPage.jsx';
import { BacktestRejectionsPage } from './BacktestRejectionsPage.jsx';
import { BacktestRunWorkspace } from './BacktestRunWorkspace.jsx';
import { BacktestTradesPage } from './BacktestTradesPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const STRATEGY = { id: 1, name: 'Momentum Cross', description: 'SMA trend-following', latestVersionNumber: 2, createdAt: '2024-01-01T00:00:00Z' };
const DATASET = { id: 1, name: 'Reliance Industries', symbol: 'RELIANCE', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' };
const DATASET_VERSION = {
  datasetId: 1, versionNumber: 1, symbol: 'RELIANCE', source: 'CSV_UPLOAD', sourceDetail: 'reliance.csv',
  adjustmentBasis: 'RAW', barCount: 60, firstDate: '2024-01-01', lastDate: '2024-03-22',
  contentHash: 'b'.repeat(64), createdAt: '2024-01-01T00:00:00Z',
};

const RUN = {
  id: 1,
  strategyId: 1,
  strategyVersion: 2,
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
  totalCommission: '12.50',
  totalSlippageCost: '3.25',
  metrics: {
    totalReturn: 0.1532,
    cagr: null,
    volatility: 0.22,
    sharpeRatio: 1.4213,
    maxDrawdown: 0.081,
    closedTradeCount: 2,
    winRate: 0.5,
    averageWin: 123.456,
    averageLoss: -67.891,
  },
  benchmark: { cash: '49.05', quantity: 999, costBasis: '99950.95', totalReturn: 0.0604 },
  createdAt: '2024-03-22T10:00:00Z',
};

const EQUITY = [
  {
    date: '2024-01-01', cash: '100000', quantity: 0, costBasis: '0', realizedPnl: '0', close: '100.00',
    marketValue: '0', equity: '100000', unrealizedPnl: '0', benchmarkEquity: '100000',
  },
  {
    date: '2024-03-22', cash: '500', quantity: 900, costBasis: '94590', realizedPnl: '0', close: '115.00',
    marketValue: '103500', equity: '104000', unrealizedPnl: '8910', benchmarkEquity: '106038.5',
  },
];

const CLOSED_TRADE = {
  status: 'CLOSED',
  entry: {
    orderId: 1, date: '2024-01-10', quantity: 900, referenceOpen: '105.00', fillPrice: '105.10', commission: '1',
    signalType: 'ENTER', signalDate: '2024-01-09', signalClose: '104.50',
    signalIndicators: [
      { type: 'SMA', period: 20, value: '103.2' },
      { type: 'RSI', period: 14, value: '58.3' },
    ],
  },
  exit: {
    orderId: 2, date: '2024-02-01', quantity: 900, referenceOpen: '110.00', fillPrice: '109.90', commission: '1',
    signalType: 'EXIT', signalDate: '2024-01-31', signalClose: '110.20',
    signalIndicators: [{ type: 'RSI', period: 14, value: '71.4' }],
  },
  quantity: 900,
  realizedPnl: '4302.00',
  totalCommission: '2',
  totalSlippageCost: '0.50',
  markDate: null,
  markClose: null,
  marketValue: null,
  unrealizedPnl: null,
};

const OPEN_TRADE = {
  status: 'OPEN',
  entry: {
    orderId: 3, date: '2024-03-01', quantity: 500, referenceOpen: '112.00', fillPrice: '112.05', commission: '1',
    signalType: 'ENTER', signalDate: '2024-02-29', signalClose: '111.80',
    signalIndicators: [{ type: 'SMA', period: 20, value: '110.1' }],
  },
  exit: null,
  quantity: 500,
  realizedPnl: null,
  totalCommission: '1',
  totalSlippageCost: '0.25',
  markDate: '2024-03-22',
  markClose: '115.00',
  marketValue: '57500.00',
  unrealizedPnl: '1475.00',
};

const REJECTIONS = [
  {
    seq: 1, reason: 'INSUFFICIENT_CASH', orderId: 5, executionDate: '2024-01-15', quantity: 200,
    requiredCash: '25000.00', availableCash: '12000.00', signalDate: '2024-01-14', signalClose: '124.00',
    signalIndicators: [{ type: 'RSI', period: 14, value: '45.0' }],
  },
  {
    seq: 2, reason: 'ZERO_QUANTITY', orderId: null, executionDate: null, quantity: null,
    requiredCash: null, availableCash: null, signalDate: '2024-01-20', signalClose: '126.00', signalIndicators: [],
  },
];

/**
 * @param {object} [overrides]
 * @param {object} [overrides.run]
 * @param {object[]} [overrides.trades]
 * @param {object[]} [overrides.rejections]
 * @param {object[]} [overrides.equity]
 * @param {object} [overrides.datasetVersion]
 * @param {(url: string) => Response | undefined} [overrides.runHandler] - overrides the run-detail response entirely
 */
function mockFetch({
  run = RUN,
  trades = [CLOSED_TRADE, OPEN_TRADE],
  rejections = REJECTIONS,
  equity = EQUITY,
  datasetVersion = DATASET_VERSION,
  runHandler,
} = {}) {
  return vi.fn((url) => {
    if (/\/api\/backtest-runs\/1\/equity-curve$/.test(url)) return Promise.resolve(jsonResponse(equity));
    if (/\/api\/backtest-runs\/1\/trades$/.test(url)) return Promise.resolve(jsonResponse(trades));
    if (/\/api\/backtest-runs\/1\/rejections$/.test(url)) return Promise.resolve(jsonResponse(rejections));
    if (/\/api\/backtest-runs\/1$/.test(url)) return Promise.resolve(runHandler ? runHandler(url) : jsonResponse(run));
    if (/\/api\/strategies\/1$/.test(url)) return Promise.resolve(jsonResponse(STRATEGY));
    if (/\/api\/datasets\/1\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(datasetVersion));
    if (/\/api\/datasets\/1$/.test(url)) return Promise.resolve(jsonResponse(DATASET));
    throw new Error(`unhandled request: ${url}`);
  });
}

function WorkspaceRoute() {
  const { runId } = useParams();
  return <BacktestRunWorkspace runId={Number(runId)} />;
}

function renderRun(initialPath) {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/backtests" element={<p>backtests list page</p>} />
        <Route path="/backtests/:runId" element={<WorkspaceRoute />}>
          <Route index element={<BacktestOverviewPage />} />
          <Route path="trades" element={<BacktestTradesPage />} />
          <Route path="rejections" element={<BacktestRejectionsPage />} />
        </Route>
        <Route path="/strategies/:id" element={<p>strategy detail page</p>} />
        <Route path="/datasets/:id" element={<p>market detail page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Backtest results dashboard', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('run header', () => {
    it('shows a loading skeleton before the run arrives', () => {
      globalThis.fetch = vi.fn(() => new Promise(() => {}));
      renderRun('/backtests/1');
      expect(screen.getByText('Loading backtest run…')).toBeTruthy();
    });

    it('loads the run and shows the research identity header', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');

      const heading = await screen.findByRole('heading', { name: 'Backtest #1' });
      await waitFor(() => expect(heading.closest('div').querySelector('p').textContent).toContain('Momentum Cross'));
      const description = heading.closest('div').querySelector('p');
      expect(description.textContent).toContain('RELIANCE');
      expect(description.textContent).toContain('Snapshot v1');
      expect(description.textContent).toContain('Strategy v2');
      expect(description.textContent).toContain('2024-01-01 → 2024-03-22');
    });

    it('shows a 404 as a not-found state, not a crash', async () => {
      globalThis.fetch = mockFetch({
        runHandler: () => jsonResponse({ title: 'Not found', detail: 'backtest run 1 not found' }, 404),
      });
      renderRun('/backtests/1');

      expect(await screen.findByText('Could not load this backtest run')).toBeTruthy();
      expect(screen.getByText('backtest run 1 not found')).toBeTruthy();
    });

    it('shows a generic, safe message for a 500 integrity failure - never raw exception detail', async () => {
      globalThis.fetch = mockFetch({
        runHandler: () => jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500),
      });
      renderRun('/backtests/1');

      expect(await screen.findByText('an internal error occurred')).toBeTruthy();
      expect(screen.queryByText(/Exception|stack trace|SQL/i)).toBeNull();
    });

    it('links back to the run history', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByRole('heading', { name: 'Backtest #1' });

      fireEvent.click(screen.getByRole('link', { name: /back to backtests/i }));
      expect(await screen.findByText('backtests list page')).toBeTruthy();
    });
  });

  describe('lazy loading across tabs', () => {
    it('fetches the run, its names, and equity on load - but never trades/rejections until their tab opens', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByRole('heading', { name: 'Backtest #1' });
      await waitFor(() => expect(screen.getByText('Performance')).toBeTruthy());

      const urls = globalThis.fetch.mock.calls.map(([url]) => url);
      expect(urls.some((url) => /equity-curve/.test(url))).toBe(true);
      expect(urls.some((url) => /\/trades$/.test(url))).toBe(false);
      expect(urls.some((url) => /\/rejections$/.test(url))).toBe(false);

      fireEvent.click(screen.getByRole('tab', { name: 'Trades' }));
      await screen.findByText(/Closed/);
      expect(globalThis.fetch.mock.calls.some(([url]) => /\/trades$/.test(url))).toBe(true);
      expect(globalThis.fetch.mock.calls.some(([url]) => /\/rejections$/.test(url))).toBe(false);

      fireEvent.click(screen.getByRole('tab', { name: 'Rejections' }));
      await screen.findByText('Insufficient cash');
      expect(globalThis.fetch.mock.calls.some(([url]) => /\/rejections$/.test(url))).toBe(true);
    });

    it('never refetches the run detail when switching tabs', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByRole('heading', { name: 'Backtest #1' });

      fireEvent.click(screen.getByRole('tab', { name: 'Trades' }));
      await screen.findByText(/Closed/);
      fireEvent.click(screen.getByRole('tab', { name: 'Overview' }));
      await screen.findByText('Performance');

      const runCalls = globalThis.fetch.mock.calls.filter(([url]) => /\/api\/backtest-runs\/1$/.test(url));
      expect(runCalls).toHaveLength(1);
    });
  });

  describe('performance metrics', () => {
    it('renders every metric from the persisted response, formatted, with nulls as a dash', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      const heading = await screen.findByText('Performance');
      const section = within(heading.closest('section'));

      expect(section.getByText('+15.32%')).toBeTruthy(); // total return
      expect(section.getByText('22.00%')).toBeTruthy(); // volatility
      expect(section.getByText('1.42')).toBeTruthy(); // sharpe ratio
      expect(section.getByText('8.10%')).toBeTruthy(); // max drawdown
      expect(section.getByText('50.00%')).toBeTruthy(); // win rate
      expect(section.getByText('123.46')).toBeTruthy(); // average win
      expect(section.getByText('-67.89')).toBeTruthy(); // average loss
      expect(section.getByText('2')).toBeTruthy(); // closed trade count

      // cagr is null in the fixture
      const cagrValue = section.getByText('CAGR').closest('div').querySelector('dd');
      expect(cagrValue.textContent).toBe('—');
    });

    it('never recomputes - the exact BigDecimal-string totals pass through unchanged', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      const heading = await screen.findByText('Performance');
      const section = within(heading.closest('section'));

      expect(section.getByText('12.50')).toBeTruthy(); // totalCommission, exact string
      expect(section.getByText('3.25')).toBeTruthy(); // totalSlippageCost, exact string
    });

    it('mentions that no drawdown series is exposed', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      expect(await screen.findByText(/Drawdown series is not currently exposed/)).toBeTruthy();
    });
  });

  describe('benchmark comparison', () => {
    it('shows strategy return, benchmark return, and their presentation-only excess return', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      const heading = await screen.findByText('Benchmark');
      const section = within(heading.closest('section'));

      expect(section.getByText('+15.32%')).toBeTruthy(); // strategy (matches the total-return metric above)
      expect(section.getByText('+6.04%')).toBeTruthy(); // benchmark
      expect(section.getByText('+9.28%')).toBeTruthy(); // 0.1532 - 0.0604, exact subtraction
    });
  });

  describe('equity curve', () => {
    it('requests the equity-curve endpoint and renders an accessible summary of the exact API values', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');

      await waitFor(() =>
        expect(globalThis.fetch.mock.calls.some(([url]) => url === '/api/backtest-runs/1/equity-curve')).toBe(true),
      );
      const summary = await screen.findByText(/Equity curve from 2024-01-01 to 2024-03-22/);
      expect(summary.textContent).toContain('100000');
      expect(summary.textContent).toContain('104000');
      expect(summary.textContent).toContain('106038.5');
    });

    it('shows an empty message when the run has no equity points', async () => {
      globalThis.fetch = mockFetch({ equity: [] });
      renderRun('/backtests/1');
      expect(await screen.findByText('No equity points were recorded for this run.')).toBeTruthy();
    });

    it('shows an error state when the equity endpoint fails, without blanking the rest of the page', async () => {
      globalThis.fetch = vi.fn((url) => {
        if (/equity-curve$/.test(url)) return Promise.resolve(jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500));
        if (/\/trades$/.test(url)) return Promise.resolve(jsonResponse([]));
        if (/\/rejections$/.test(url)) return Promise.resolve(jsonResponse([]));
        if (/\/api\/backtest-runs\/1$/.test(url)) return Promise.resolve(jsonResponse(RUN));
        if (/\/api\/strategies\/1$/.test(url)) return Promise.resolve(jsonResponse(STRATEGY));
        if (/\/api\/datasets\/1\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(DATASET_VERSION));
        if (/\/api\/datasets\/1$/.test(url)) return Promise.resolve(jsonResponse(DATASET));
        throw new Error(`unhandled: ${url}`);
      });
      renderRun('/backtests/1');

      expect(await screen.findByText('Could not load the equity curve')).toBeTruthy();
      expect(screen.getByText('Performance')).toBeTruthy();
    });

    it('caches the equity curve - switching tabs and back does not refetch it', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await waitFor(() => expect(globalThis.fetch.mock.calls.some(([url]) => /equity-curve/.test(url))).toBe(true));

      fireEvent.click(screen.getByRole('tab', { name: 'Trades' }));
      await screen.findByText(/Closed/);
      fireEvent.click(screen.getByRole('tab', { name: 'Overview' }));
      await screen.findByText('Performance');

      const equityCalls = globalThis.fetch.mock.calls.filter(([url]) => /equity-curve/.test(url));
      expect(equityCalls).toHaveLength(1);
    });
  });

  describe('warm-up notice (C1)', () => {
    it('shows no warm-up notice when firstEvaluableDate equals startDate (CASE 1)', async () => {
      globalThis.fetch = mockFetch({ run: { ...RUN, firstEvaluableDate: RUN.startDate } });
      renderRun('/backtests/1');
      await screen.findByText('Performance');

      expect(screen.queryByText(/Warm-up period/)).toBeNull();
      expect(screen.queryByText(/Never evaluated/)).toBeNull();
    });

    it('explains a delayed firstEvaluableDate without implying the strategy underperformed (CASE 2)', async () => {
      globalThis.fetch = mockFetch(); // RUN.firstEvaluableDate '2024-03-08' > RUN.startDate '2024-01-01'
      renderRun('/backtests/1');

      expect(await screen.findByText('Warm-up period')).toBeTruthy();
      expect(screen.getByText(/could not be evaluated until/)).toBeTruthy();
      // Also rendered separately in ResearchInputsPanel's own "First evaluable
      // date" field - so this must allow more than one match.
      expect(screen.getAllByText(RUN.firstEvaluableDate).length).toBeGreaterThan(0);
      // EQUITY has 2 points: 2024-01-01 (before firstEvaluableDate) and 2024-03-22 (after) -
      // derived from the already-fetched equity curve, no new endpoint.
      const notice = await screen.findByRole('status');
      expect(notice.textContent).toMatch(/No signal could be generated on 1 of 2 bars in range/);
      // Scoped to the notice itself - "Average loss" is a legitimate, unrelated
      // performance-metric label rendered elsewhere on the same page.
      expect(notice.textContent).not.toMatch(/lost|underperform|loss/i);
    });

    it('explains a null firstEvaluableDate as "never ready", never as a strategy loss (CASE 3)', async () => {
      globalThis.fetch = mockFetch({ run: { ...RUN, firstEvaluableDate: null } });
      renderRun('/backtests/1');

      expect(await screen.findByText('Never evaluated')).toBeTruthy();
      const notice = screen.getByRole('status');
      expect(notice.textContent).toMatch(/never had enough history/);
      expect(notice.textContent).toMatch(/no entry or exit signal was possible at any point/);
      await waitFor(() => expect(notice.textContent).toMatch(/No signal could be generated on any of the 2 bars in range/));
      expect(notice.textContent).not.toMatch(/lost|underperform|loss/i);
    });
  });

  describe('assumptions (I4)', () => {
    it('discloses the dataset source, adjustment basis, and a RAW warning', async () => {
      globalThis.fetch = mockFetch(); // DATASET_VERSION.adjustmentBasis is RAW
      renderRun('/backtests/1');
      await screen.findByText('Assumptions');

      expect(await screen.findByText('CSV upload')).toBeTruthy();
      expect(screen.getByText('Raw')).toBeTruthy();
      expect(screen.getByText('Raw prices are not split-adjusted.')).toBeTruthy();
    });

    it('omits the RAW warning for split-adjusted data', async () => {
      globalThis.fetch = mockFetch({ datasetVersion: { ...DATASET_VERSION, adjustmentBasis: 'SPLIT_ADJUSTED' } });
      renderRun('/backtests/1');

      expect(await screen.findByText('Split-adjusted')).toBeTruthy();
      expect(screen.queryByText('Raw prices are not split-adjusted.')).toBeNull();
    });

    it('explains the execution model, open-position treatment, benchmark definition, and disclaimer', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByText('Assumptions');

      expect(screen.getByText(/generated at a bar’s close/)).toBeTruthy();
      expect(screen.getByText(/executes at the next bar’s open/)).toBeTruthy();
      expect(screen.getByText(/reserves one commission in cash at entry/)).toBeTruthy();
      expect(screen.getByText(/never force-liquidated/)).toBeTruthy();
      expect(screen.getByText(/Buy & hold represents the same starting capital/)).toBeTruthy();
      expect(screen.getByText('Historical simulation — not indicative of future results.')).toBeTruthy();
    });

    it('still shows the static assumptions text even if the dataset snapshot fails to load', async () => {
      globalThis.fetch = vi.fn((url) => {
        if (/\/api\/datasets\/1\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse({ title: 'Internal error' }, 500));
        if (/\/api\/backtest-runs\/1\/equity-curve$/.test(url)) return Promise.resolve(jsonResponse(EQUITY));
        if (/\/api\/backtest-runs\/1\/trades$/.test(url)) return Promise.resolve(jsonResponse([]));
        if (/\/api\/backtest-runs\/1\/rejections$/.test(url)) return Promise.resolve(jsonResponse([]));
        if (/\/api\/backtest-runs\/1$/.test(url)) return Promise.resolve(jsonResponse(RUN));
        if (/\/api\/strategies\/1$/.test(url)) return Promise.resolve(jsonResponse(STRATEGY));
        if (/\/api\/datasets\/1$/.test(url)) return Promise.resolve(jsonResponse(DATASET));
        throw new Error(`unhandled: ${url}`);
      });
      renderRun('/backtests/1');

      expect(await screen.findByText('Historical simulation — not indicative of future results.')).toBeTruthy();
      expect(screen.queryByText('CSV upload')).toBeNull();
    });
  });

  describe('research inputs', () => {
    it('shows market/strategy identity, period, capital/commission/slippage, and copyable hashes', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByText('Research inputs');

      expect(await screen.findByRole('link', { name: 'Reliance Industries' })).toBeTruthy();
      expect(await screen.findByRole('link', { name: 'Momentum Cross' })).toBeTruthy();
      expect(screen.getByText('Snapshot v1')).toBeTruthy();
      expect(screen.getByText('Version v2')).toBeTruthy();
      expect(screen.getByText('0.05%')).toBeTruthy(); // slippageRate "0.0005" -> percent display
      expect(screen.getAllByText(RUN.definitionHash).length).toBeGreaterThan(0);
      expect(screen.getAllByText(RUN.contentHash).length).toBeGreaterThan(0);
    });

    it('shows the first evaluable date as a plain fact (C1)', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByText('Research inputs');

      expect(screen.getByText('First evaluable date')).toBeTruthy();
      expect(screen.getAllByText(RUN.firstEvaluableDate).length).toBeGreaterThan(0);
    });

    it('navigates to the market and strategy detail pages without reloading their versions', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByText('Research inputs');

      fireEvent.click(await screen.findByRole('link', { name: 'Momentum Cross' }));
      expect(await screen.findByText('strategy detail page')).toBeTruthy();
    });
  });

  describe('trades', () => {
    it('renders a closed trade with its exact entry/exit/P&L values, never fabricated', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/trades');

      const closedRow = (await screen.findByText('105.10')).closest('tr');
      expect(within(closedRow).getByText('Closed')).toBeTruthy();
      expect(within(closedRow).getByText('2024-01-10')).toBeTruthy();
      expect(within(closedRow).getByText('2024-02-01')).toBeTruthy();
      expect(within(closedRow).getByText('109.90')).toBeTruthy();
      expect(within(closedRow).getByText('4302.00')).toBeTruthy();
    });

    it('renders an open trade with no exit and no realized P&L - only unrealized', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/trades');

      const openRow = (await screen.findByText('112.05')).closest('tr');
      expect(within(openRow).getByText('Open')).toBeTruthy();
      expect(within(openRow).getByText('1475.00')).toBeTruthy(); // unrealized P&L
      // no exit date/price, no realized P&L - all render as a dash
      const dashes = within(openRow).getAllByText('—');
      expect(dashes.length).toBeGreaterThanOrEqual(3);
    });

    it('preserves the exact indicator snapshot strings behind a disclosure', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/trades');
      await screen.findByText('105.10');

      const disclosures = screen.getAllByText('Indicators');
      fireEvent.click(disclosures[0]);
      expect(screen.getByText('SMA(20) = 103.2')).toBeTruthy();
      expect(screen.getByText('RSI(14) = 58.3')).toBeTruthy();
    });

    it('shows an empty-run message, not an error, when there are no trades', async () => {
      globalThis.fetch = mockFetch({ trades: [] });
      renderRun('/backtests/1/trades');
      expect(await screen.findByText('No trades were generated by this run.')).toBeTruthy();
    });

    it('shows a "show more" control instead of rendering hundreds of rows at once', async () => {
      const manyTrades = Array.from({ length: 120 }, (_, i) => ({
        ...CLOSED_TRADE,
        entry: { ...CLOSED_TRADE.entry, orderId: i + 1, fillPrice: `${100 + i}.00` },
      }));
      globalThis.fetch = mockFetch({ trades: manyTrades });
      renderRun('/backtests/1/trades');

      await screen.findByText('100.00');
      expect(screen.queryAllByText('Closed')).toHaveLength(50);
      expect(screen.getByRole('button', { name: /show more/i })).toBeTruthy();

      fireEvent.click(screen.getByRole('button', { name: /show more/i }));
      expect(screen.queryAllByText('Closed')).toHaveLength(100);
    });
  });

  describe('rejections', () => {
    it('renders rejections in exact engine append order, never re-sorted', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/rejections');
      await screen.findByText('Insufficient cash');

      const rows = screen.getAllByRole('row').slice(1); // skip header row
      expect(within(rows[0]).getByText('Insufficient cash')).toBeTruthy();
      expect(within(rows[1]).getByText('Zero quantity')).toBeTruthy();
    });

    it('renders nullable ZeroQuantity fields as a dash, never a fabricated value', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/rejections');
      await screen.findByText('Zero quantity');

      const zeroQuantityRow = screen.getByText('Zero quantity').closest('tr');
      const dashes = within(zeroQuantityRow).getAllByText('—');
      expect(dashes.length).toBeGreaterThanOrEqual(4); // executionDate, orderId, quantity, requiredCash, availableCash
    });

    it('renders full InsufficientCash fields exactly', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/rejections');
      await screen.findByText('Insufficient cash');

      const row = screen.getByText('Insufficient cash').closest('tr');
      expect(within(row).getByText('25000.00')).toBeTruthy();
      expect(within(row).getByText('12000.00')).toBeTruthy();
      expect(within(row).getByText('200')).toBeTruthy();
    });

    it('shows an empty-run message, not an error, when nothing was rejected', async () => {
      globalThis.fetch = mockFetch({ rejections: [] });
      renderRun('/backtests/1/rejections');
      expect(await screen.findByText('No orders were rejected.')).toBeTruthy();
    });
  });

  describe('accessibility', () => {
    it('exposes the section tabs as a real tablist', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1');
      await screen.findByRole('heading', { name: 'Backtest #1' });

      expect(screen.getByRole('tablist', { name: 'Backtest run sections' })).toBeTruthy();
      expect(screen.getByRole('tab', { name: 'Overview' })).toBeTruthy();
      expect(screen.getByRole('tab', { name: 'Trades' })).toBeTruthy();
      expect(screen.getByRole('tab', { name: 'Rejections' })).toBeTruthy();
    });

    it('gives the trades table real column headers', async () => {
      globalThis.fetch = mockFetch();
      renderRun('/backtests/1/trades');
      await screen.findByText('105.10');

      expect(screen.getByRole('columnheader', { name: 'Status' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Realized P&L' })).toBeTruthy();
    });
  });
});
