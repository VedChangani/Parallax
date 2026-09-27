import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { StrategyVersionPage } from './StrategyVersionPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const sampleStrategy = {
  id: 42,
  name: 'Momentum Cross',
  description: 'SMA trend-following strategy',
  latestVersionNumber: 2,
  createdAt: '2024-01-01T00:00:00Z',
};

const sampleVersion = {
  strategyId: 42,
  versionNumber: 2,
  schemaVersion: 1,
  definitionHash: 'd'.repeat(64),
  createdAt: '2024-02-01T00:00:00Z',
  definition: {
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
  },
};

function stubFetch() {
  globalThis.fetch = vi.fn((url) => {
    if (/\/versions\/2$/.test(url)) return Promise.resolve(jsonResponse(sampleVersion));
    return Promise.resolve(jsonResponse(sampleStrategy));
  });
}

function renderVersion() {
  return render(
    <MemoryRouter initialEntries={['/strategies/42/versions/2']}>
      <Routes>
        <Route path="/strategies/:id" element={<p>strategy detail page</p>} />
        <Route path="/strategies/:id/versions/new" element={<p>new version page</p>} />
        <Route path="/strategies/:id/versions/:version" element={<StrategyVersionPage strategyId={42} versionNumber={2} />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('StrategyVersionPage', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the definition as human-readable text, never raw JSON', async () => {
    stubFetch();
    renderVersion();

    expect(await screen.findByRole('heading', { name: 'Momentum Cross — v2' })).toBeTruthy();
    expect(screen.getByText('Immutable')).toBeTruthy();
    expect(screen.getByText('SMA(20) > SMA(50)')).toBeTruthy();
    expect(screen.getByText('RSI(14) < 30')).toBeTruthy();
    expect(screen.getByText('100% of available cash')).toBeTruthy();
    expect(screen.queryByText(/"type":"compare"/)).toBeNull();
    expect(screen.queryByText('schemaVersion')).toBeNull();
  });

  it('shows the definition hash with a copy control', async () => {
    stubFetch();
    renderVersion();

    expect(await screen.findByText(sampleVersion.definitionHash)).toBeTruthy();
  });

  it('navigates back to the strategy detail page', async () => {
    stubFetch();
    renderVersion();

    const link = await screen.findByRole('link', { name: /back to momentum cross/i });
    fireEvent.click(link);

    expect(await screen.findByText('strategy detail page')).toBeTruthy();
  });

  it('caches the version under its exact URL as the immutable cache key', async () => {
    stubFetch();
    renderVersion();

    await screen.findByRole('heading', { name: 'Momentum Cross — v2' });
    expect(immutableCache.has('/api/strategies/42/versions/2')).toBe(true);
    expect(immutableCache.get('/api/strategies/42/versions/2')).toEqual(sampleVersion);
  });

  it('never shows an editable control - the version is read-only', async () => {
    stubFetch();
    renderVersion();

    await screen.findByRole('heading', { name: 'Momentum Cross — v2' });
    expect(screen.queryByRole('textbox')).toBeNull();
    expect(screen.queryByRole('spinbutton')).toBeNull();
  });

  it('navigates to "Create new version" carrying this version number as the starting point', async () => {
    stubFetch();
    renderVersion();

    const button = await screen.findByRole('button', { name: /create new version from v2/i });
    fireEvent.click(button);

    expect(await screen.findByText('new version page')).toBeTruthy();
  });
});
