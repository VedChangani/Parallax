import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { BacktestNewPage } from './BacktestNewPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: body === undefined ? {} : { 'content-type': 'application/json' },
  });
}

const MARKET = { id: 7, name: 'Reliance Industries', symbol: 'RELIANCE', latestVersionNumber: 3, createdAt: '2024-01-01T00:00:00Z' };

const SNAPSHOT_V3 = {
  datasetId: 7,
  versionNumber: 3,
  symbol: 'RELIANCE',
  source: 'ALPHA_VANTAGE',
  sourceDetail: 'full',
  adjustmentBasis: 'SPLIT_ADJUSTED',
  barCount: 2145,
  firstDate: '2018-01-01',
  lastDate: '2026-09-25',
  contentHash: 'c'.repeat(64),
  createdAt: '2024-01-01T00:00:00Z',
};
const SNAPSHOT_V2 = { ...SNAPSHOT_V3, versionNumber: 2, barCount: 1900, firstDate: '2016-01-01', lastDate: '2023-01-01' };

const STRATEGY = { id: 42, name: 'Momentum Cross', description: 'SMA trend-following', latestVersionNumber: 4, createdAt: '2024-01-01T00:00:00Z' };

const DEFINITION_V4 = {
  entryCondition: {
    type: 'all',
    conditions: [
      { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
      { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '70' } },
    ],
  },
  exitCondition: { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'GT', right: { type: 'constant', value: '70' } },
  positionSizing: { type: 'cashFraction', fraction: '1' },
};

const STRATEGY_VERSION_V4 = { strategyId: 42, versionNumber: 4, schemaVersion: 1, definitionHash: 'a'.repeat(64), createdAt: '2024-01-01T00:00:00Z', definition: DEFINITION_V4 };
const STRATEGY_VERSION_V3 = { ...STRATEGY_VERSION_V4, versionNumber: 3, definitionHash: 'b'.repeat(64) };

const VALID_CONFIG_INPUT = {
  initialCapital: '100000',
  commissionPerFill: '1',
  slippagePercent: '0.05%',
  startDate: '2021-01-01',
  endDate: '2022-01-01',
};

/**
 * @param {object} [overrides]
 * @param {Array} [overrides.markets]
 * @param {Array} [overrides.strategies]
 * @param {(url: string, init: object) => Response | undefined} [overrides.postHandler]
 */
function mockFetch({ markets = [MARKET], strategies = [STRATEGY], postHandler } = {}) {
  return vi.fn((url, init = {}) => {
    if (init.method === 'POST' && url === '/api/backtest-runs') {
      return Promise.resolve(postHandler ? postHandler(url, init) : jsonResponse({ id: 501 }, 201));
    }
    if (url === '/api/datasets') return Promise.resolve(jsonResponse(markets));
    if (url === '/api/datasets/7/versions') return Promise.resolve(jsonResponse([SNAPSHOT_V3, SNAPSHOT_V2]));
    if (url === '/api/datasets/7/versions/3') return Promise.resolve(jsonResponse(SNAPSHOT_V3));
    if (url === '/api/datasets/7/versions/2') return Promise.resolve(jsonResponse(SNAPSHOT_V2));
    if (url === '/api/strategies') return Promise.resolve(jsonResponse(strategies));
    if (url === '/api/strategies/42/versions') {
      return Promise.resolve(
        jsonResponse(
          [STRATEGY_VERSION_V4, STRATEGY_VERSION_V3].map((version) => ({
            strategyId: version.strategyId,
            versionNumber: version.versionNumber,
            schemaVersion: version.schemaVersion,
            definitionHash: version.definitionHash,
            createdAt: version.createdAt,
          })),
        ),
      );
    }
    if (url === '/api/strategies/42/versions/4') return Promise.resolve(jsonResponse(STRATEGY_VERSION_V4));
    if (url === '/api/strategies/42/versions/3') return Promise.resolve(jsonResponse(STRATEGY_VERSION_V3));
    throw new Error(`unhandled request: ${init.method ?? 'GET'} ${url}`);
  });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/backtests/new']}>
      <Routes>
        <Route path="/backtests/new" element={<BacktestNewPage />} />
        <Route path="/backtests/:runId" element={<p>run detail page</p>} />
        <Route path="/datasets" element={<p>markets page</p>} />
        <Route path="/strategies" element={<p>strategies page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillValidConfig() {
  fireEvent.change(screen.getByLabelText('Initial capital'), { target: { value: VALID_CONFIG_INPUT.initialCapital } });
  fireEvent.change(screen.getByLabelText('Commission per fill'), { target: { value: VALID_CONFIG_INPUT.commissionPerFill } });
  fireEvent.change(screen.getByLabelText('Slippage'), { target: { value: VALID_CONFIG_INPUT.slippagePercent } });
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: VALID_CONFIG_INPUT.startDate } });
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: VALID_CONFIG_INPUT.endDate } });
}

describe('BacktestNewPage', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('loads markets and strategies and defaults to the latest snapshot/version', async () => {
    globalThis.fetch = mockFetch();
    renderPage();

    expect(await screen.findByDisplayValue('Reliance Industries (RELIANCE)')).toBeTruthy();
    expect((await screen.findAllByText(/2018-01-01 → 2026-09-25/)).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('Data snapshot')).toHaveProperty('value', '3');

    expect(await screen.findByDisplayValue('Momentum Cross')).toBeTruthy();
    expect(screen.getByLabelText('Version')).toHaveProperty('value', '4');
  });

  it('shows a useful empty state and no fake data when there are no markets', async () => {
    globalThis.fetch = mockFetch({ markets: [] });
    renderPage();

    expect(await screen.findByText('No markets available')).toBeTruthy();
    fireEvent.click(screen.getByRole('link', { name: 'Go to Markets' }));
    expect(await screen.findByText('markets page')).toBeTruthy();
  });

  it('shows a useful empty state when there are no strategies', async () => {
    globalThis.fetch = mockFetch({ strategies: [] });
    renderPage();

    expect(await screen.findByText('No strategies available')).toBeTruthy();
    fireEvent.click(screen.getByRole('link', { name: 'Go to Strategies' }));
    expect(await screen.findByText('strategies page')).toBeTruthy();
  });

  it('shows an error state with retry when markets fail to load', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (url === '/api/datasets') return Promise.resolve(jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500));
      if (url === '/api/strategies') return Promise.resolve(jsonResponse([STRATEGY]));
      throw new Error(`unhandled request: ${url}`);
    });
    renderPage();

    expect(await screen.findByText('Could not load markets')).toBeTruthy();
    expect(screen.getByText('an internal error occurred')).toBeTruthy();
  });

  it('renders the selected strategy version definition preview exactly', async () => {
    globalThis.fetch = mockFetch();
    renderPage();

    await screen.findByText(/SMA\(20\) > SMA\(50\)/);
    expect(screen.getByText(/RSI\(14\) < 70/)).toBeTruthy();
    expect(screen.getByText(/RSI\(14\) > 70/)).toBeTruthy();
  });

  it('resets the selected snapshot/version when the parent market/strategy changes', async () => {
    globalThis.fetch = mockFetch();
    renderPage();

    await waitFor(() => expect(screen.getByLabelText('Data snapshot')).toHaveProperty('value', '3'));
    fireEvent.change(screen.getByLabelText('Data snapshot'), { target: { value: '2' } });
    expect(screen.getByLabelText('Data snapshot')).toHaveProperty('value', '2');
    expect((await screen.findAllByText(/2016-01-01 → 2023-01-01/)).length).toBeGreaterThan(0);
  });

  it('never sends a request while required fields are empty, and reports every validation error at once', async () => {
    globalThis.fetch = mockFetch();
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    expect(await screen.findByText('Initial capital is required.')).toBeTruthy();
    expect(screen.getByText('Commission per fill is required.')).toBeTruthy();
    expect(screen.getByText('Slippage is required.')).toBeTruthy();
    expect(globalThis.fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it('rejects a malformed decimal and an invalid date range without calling the backend', async () => {
    globalThis.fetch = mockFetch();
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    fireEvent.change(screen.getByLabelText('Initial capital'), { target: { value: '1,000' } });
    fireEvent.change(screen.getByLabelText('Commission per fill'), { target: { value: '0' } });
    fireEvent.change(screen.getByLabelText('Slippage'), { target: { value: '0.05%' } });
    fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-01-01' } });
    fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2021-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    expect(await screen.findByText('Enter a valid amount, e.g. 100000.')).toBeTruthy();
    expect(screen.getByText('End date must be on or after the start date.')).toBeTruthy();
    expect(globalThis.fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it('posts the exact CreateBacktestRunRequest shape with decimal fields as strings and the correct slippage fraction', async () => {
    globalThis.fetch = mockFetch();
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    await screen.findByText('run detail page');

    const postCall = globalThis.fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    const body = JSON.parse(postCall[1].body);
    expect(body).toEqual({
      strategyId: 42,
      strategyVersion: 4,
      datasetId: 7,
      datasetVersion: 3,
      config: {
        initialCapital: '100000',
        commissionPerFill: '1',
        slippageRate: '0.0005',
        startDate: '2021-01-01',
        endDate: '2022-01-01',
      },
    });
    expect(typeof body.config.initialCapital).toBe('string');
    expect(typeof body.config.commissionPerFill).toBe('string');
    expect(typeof body.config.slippageRate).toBe('string');
    expect(body).not.toHaveProperty('ownerId');
    expect(body).not.toHaveProperty('status');
    expect(body).not.toHaveProperty('createdAt');
    expect(body).not.toHaveProperty('engineSemanticsVersion');
  });

  it('navigates to /backtests/{runId} on a 201', async () => {
    globalThis.fetch = mockFetch({ postHandler: () => jsonResponse({ id: 777 }, 201) });
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    expect(await screen.findByText('run detail page')).toBeTruthy();
  });

  it('shows a range-specific message for a 422 BacktestRangeException without discarding the entered configuration', async () => {
    globalThis.fetch = mockFetch({
      postHandler: () =>
        jsonResponse(
          {
            title: 'Invalid backtest range',
            detail: 'requested range is outside dataset coverage',
            requestedStartDate: '2021-01-01',
            requestedEndDate: '2022-01-01',
            datasetFirstDate: '2018-01-01',
            datasetLastDate: '2026-09-25',
          },
          422,
        ),
    });
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    expect(await screen.findByText('Selected dates are outside the available historical data.')).toBeTruthy();
    expect(screen.getByText('Available data: 2018-01-01 → 2026-09-25')).toBeTruthy();
    expect(screen.getByLabelText('Start date')).toHaveProperty('value', VALID_CONFIG_INPUT.startDate);
  });

  it.each([
    [400, 'Malformed request'],
    [404, 'Not found'],
    [409, 'Conflict'],
    [500, 'Internal error'],
  ])('shows a safe error for a %i response', async (status, title) => {
    globalThis.fetch = mockFetch({ postHandler: () => jsonResponse({ title, detail: `${title} detail` }, status) });
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    expect(await screen.findByText(`${title} detail`)).toBeTruthy();
  });

  it('shows a network error without navigating', async () => {
    globalThis.fetch = mockFetch({ postHandler: () => Promise.reject(new TypeError('Failed to fetch')) });
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    expect(await screen.findByText(/Could not reach the backend/)).toBeTruthy();
    expect(screen.queryByText('run detail page')).toBeNull();
  });

  it('disables the button while submitting and sends only one POST on a double click', async () => {
    let resolvePost;
    const postPromise = new Promise((resolve) => {
      resolvePost = resolve;
    });
    globalThis.fetch = mockFetch({ postHandler: () => postPromise });
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    const button = screen.getByRole('button', { name: /run backtest/i });
    fireEvent.click(button);
    fireEvent.click(button);

    await waitFor(() => expect(button.disabled).toBe(true));
    expect(screen.getByText(/running backtest/i)).toBeTruthy();

    resolvePost(jsonResponse({ id: 501 }, 201));
    await screen.findByText('run detail page');

    const postCalls = globalThis.fetch.mock.calls.filter(([, init]) => init?.method === 'POST');
    expect(postCalls).toHaveLength(1);
  });

  it('never polls or requests a status endpoint after submitting', async () => {
    globalThis.fetch = mockFetch();
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    await fillValidConfig();
    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));
    await screen.findByText('run detail page');

    const urls = globalThis.fetch.mock.calls.map(([url]) => url);
    expect(urls.some((url) => /status|job|poll/i.test(url))).toBe(false);
  });

  it('associates field errors with their inputs for assistive technology', async () => {
    globalThis.fetch = mockFetch();
    renderPage();
    await waitFor(() => expect(screen.getByLabelText('Version')).toHaveProperty('value', '4'));

    fireEvent.click(screen.getByRole('button', { name: /run backtest/i }));

    const capitalInput = await screen.findByLabelText('Initial capital');
    expect(capitalInput.getAttribute('aria-invalid')).toBe('true');
    const describedBy = capitalInput.getAttribute('aria-describedby');
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy).textContent).toBe('Initial capital is required.');
  });
});
