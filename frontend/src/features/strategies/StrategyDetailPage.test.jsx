import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { StrategyDetailPage } from './StrategyDetailPage.jsx';

function NewBacktestProbe() {
  const location = useLocation();
  return <p>new backtest page · strategyId={String(location.state?.strategyId)}</p>;
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const strategyV1 = { id: 42, name: 'Momentum Cross', description: 'SMA trend-following strategy', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' };
const strategyRenamed = { ...strategyV1, name: 'Momentum Cross v2', description: 'Updated description' };

const versionSummaries = [{ strategyId: 42, versionNumber: 1, schemaVersion: 1, definitionHash: 'a'.repeat(64), createdAt: '2024-01-01T00:00:00Z' }];

const versionDetail = {
  strategyId: 42,
  versionNumber: 1,
  schemaVersion: 1,
  definitionHash: 'a'.repeat(64),
  createdAt: '2024-01-01T00:00:00Z',
  definition: {
    entryCondition: { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
    exitCondition: { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '30' } },
    positionSizing: { type: 'cashFraction', fraction: '1' },
  },
};

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/strategies/42']}>
      <Routes>
        <Route path="/strategies" element={<p>strategies list page</p>} />
        <Route path="/strategies/:id" element={<StrategyDetailPage strategyId={42} />} />
        <Route path="/strategies/:id/versions/new" element={<p>new version page</p>} />
        <Route path="/backtests/new" element={<NewBacktestProbe />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('StrategyDetailPage', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders metadata, the latest version definition, and version history', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(versionDetail));
      if (/\/versions$/.test(url)) return Promise.resolve(jsonResponse(versionSummaries));
      return Promise.resolve(jsonResponse(strategyV1));
    });

    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Momentum Cross' })).toBeTruthy();
    expect(screen.getByText('SMA trend-following strategy')).toBeTruthy();
    expect(await screen.findByText('SMA(20) > SMA(50)')).toBeTruthy();
    expect(screen.getAllByText('v1').length).toBeGreaterThan(0);
  });

  it('edits metadata via PATCH and refetches the strategy (never caching it)', async () => {
    globalThis.fetch = vi.fn((url, init) => {
      if (init?.method === 'PATCH') return Promise.resolve(jsonResponse(strategyRenamed));
      if (/\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(versionDetail));
      if (/\/versions$/.test(url)) return Promise.resolve(jsonResponse(versionSummaries));
      // First GET returns the original, every later GET returns the renamed strategy (as the backend would after a PATCH).
      return Promise.resolve(jsonResponse(globalThis.__patched ? strategyRenamed : strategyV1));
    });

    renderDetail();
    await screen.findByRole('heading', { name: 'Momentum Cross' });

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Momentum Cross v2' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Updated description' } });
    globalThis.__patched = true;
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('heading', { name: 'Momentum Cross v2' })).toBeTruthy();
    expect(screen.getByText('Updated description')).toBeTruthy();

    const patchCall = globalThis.fetch.mock.calls.find(([, init]) => init?.method === 'PATCH');
    expect(patchCall[0]).toBe('/api/strategies/42');
    expect(JSON.parse(patchCall[1].body)).toEqual({ name: 'Momentum Cross v2', description: 'Updated description' });

    delete globalThis.__patched;
  });

  it('never edits any version content from the metadata form', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(versionDetail));
      if (/\/versions$/.test(url)) return Promise.resolve(jsonResponse(versionSummaries));
      return Promise.resolve(jsonResponse(strategyV1));
    });

    renderDetail();
    await screen.findByRole('heading', { name: 'Momentum Cross' });
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

    expect(screen.queryByText('v1', { selector: 'input' })).toBeNull();
    expect(screen.queryByLabelText(/version/i)).toBeNull();
    expect(screen.queryByLabelText(/hash/i)).toBeNull();
  });

  it('navigates to "Create new version" with no source version pre-selected (defaults to latest)', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(versionDetail));
      if (/\/versions$/.test(url)) return Promise.resolve(jsonResponse(versionSummaries));
      return Promise.resolve(jsonResponse(strategyV1));
    });

    renderDetail();
    await screen.findByRole('heading', { name: 'Momentum Cross' });
    fireEvent.click(screen.getByRole('button', { name: 'Create new version' }));

    expect(await screen.findByText('new version page')).toBeTruthy();
  });

  it('links "Create backtest" to /backtests/new with this strategy preselected', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(versionDetail));
      if (/\/versions$/.test(url)) return Promise.resolve(jsonResponse(versionSummaries));
      return Promise.resolve(jsonResponse(strategyV1));
    });

    renderDetail();
    await screen.findByRole('heading', { name: 'Momentum Cross' });
    fireEvent.click(screen.getByRole('link', { name: 'Create backtest' }));

    expect(await screen.findByText('new backtest page · strategyId=42')).toBeTruthy();
  });

  it('shows an empty state when there are no versions yet', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions$/.test(url)) return Promise.resolve(jsonResponse([]));
      return Promise.resolve(jsonResponse({ ...strategyV1, latestVersionNumber: 0 }));
    });

    renderDetail();
    expect(await screen.findByText('No versions yet')).toBeTruthy();
  });

  it('shows an error state when the strategy cannot be loaded', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ title: 'Not found', detail: 'strategy 42 not found' }, 404));

    renderDetail();
    expect(await screen.findByText('strategy 42 not found')).toBeTruthy();
  });
});
