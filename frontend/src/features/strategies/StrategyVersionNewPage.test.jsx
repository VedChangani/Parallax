import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { StrategyVersionNewPage } from './StrategyVersionNewPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const strategy = { id: 42, name: 'Momentum Cross', description: 'SMA trend-following strategy', latestVersionNumber: 2, createdAt: '2024-01-01T00:00:00Z' };

const definitionV2 = {
  entryCondition: { type: 'compare', left: { type: 'indicator', indicator: 'SMA', period: 20 }, operator: 'GT', right: { type: 'indicator', indicator: 'SMA', period: 50 } },
  exitCondition: { type: 'compare', left: { type: 'indicator', indicator: 'RSI', period: 14 }, operator: 'LT', right: { type: 'constant', value: '30' } },
  positionSizing: { type: 'cashFraction', fraction: '1' },
};

const versionV2 = { strategyId: 42, versionNumber: 2, schemaVersion: 1, definitionHash: 'a'.repeat(64), createdAt: '2024-01-01T00:00:00Z', definition: definitionV2 };
const versionV1 = { ...versionV2, versionNumber: 1, definitionHash: 'b'.repeat(64) };

function renderAt(initialEntries) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <Routes>
        <Route path="/strategies/:id" element={<p>strategy detail page</p>} />
        <Route path="/strategies/:id/versions/new" element={<StrategyVersionNewPage strategyId={42} />} />
        <Route path="/strategies/:id/versions/:version" element={<p>version detail page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('StrategyVersionNewPage', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts from the latest version definition when no source version is specified', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/2$/.test(url)) return Promise.resolve(jsonResponse(versionV2));
      return Promise.resolve(jsonResponse(strategy));
    });

    renderAt(['/strategies/42/versions/new']);

    expect(await screen.findByText(/starting from v2/i)).toBeTruthy();
    expect(screen.getAllByLabelText('Left operand indicator')[0]).toHaveProperty('value', 'SMA');
    expect(screen.getAllByLabelText('Left operand period')[0]).toHaveProperty('value', '20');
  });

  it('starts from a specific older version when navigated here with { fromVersion }', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/1$/.test(url)) return Promise.resolve(jsonResponse(versionV1));
      return Promise.resolve(jsonResponse(strategy));
    });

    render(
      <MemoryRouter initialEntries={[{ pathname: '/strategies/42/versions/new', state: { fromVersion: 1 } }]}>
        <Routes>
          <Route path="/strategies/:id/versions/new" element={<StrategyVersionNewPage strategyId={42} />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText(/starting from v1/i)).toBeTruthy();
  });

  it('never shows a version-number input - the backend allocates it', async () => {
    globalThis.fetch = vi.fn((url) => {
      if (/\/versions\/2$/.test(url)) return Promise.resolve(jsonResponse(versionV2));
      return Promise.resolve(jsonResponse(strategy));
    });

    renderAt(['/strategies/42/versions/new']);
    await screen.findByText(/starting from v2/i);

    expect(screen.queryByLabelText(/version number/i)).toBeNull();
  });

  it('creates a new version and navigates to it', async () => {
    const created = { ...versionV2, versionNumber: 3 };
    globalThis.fetch = vi.fn((url, init) => {
      if (init?.method === 'POST') return Promise.resolve(jsonResponse(created, 201));
      if (/\/versions\/2$/.test(url)) return Promise.resolve(jsonResponse(versionV2));
      return Promise.resolve(jsonResponse(strategy));
    });

    renderAt(['/strategies/42/versions/new']);
    await screen.findByText(/starting from v2/i);

    fireEvent.click(screen.getByRole('button', { name: /create version/i }));

    expect(await screen.findByText('version detail page')).toBeTruthy();

    const postCall = globalThis.fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(postCall[0]).toBe('/api/strategies/42/versions');
    expect(JSON.parse(postCall[1].body)).toEqual(definitionV2);
  });

  it('preserves the edited builder state and shows an error on a 409 version conflict', async () => {
    globalThis.fetch = vi.fn((url, init) => {
      if (init?.method === 'POST') return Promise.resolve(jsonResponse({ title: 'Conflict', detail: 'version 3 already exists for strategy 42' }, 409));
      if (/\/versions\/2$/.test(url)) return Promise.resolve(jsonResponse(versionV2));
      return Promise.resolve(jsonResponse(strategy));
    });

    renderAt(['/strategies/42/versions/new']);
    await screen.findByText(/starting from v2/i);

    fireEvent.change(screen.getAllByLabelText('Left operand period')[0], { target: { value: '99' } });
    fireEvent.click(screen.getByRole('button', { name: /create version/i }));

    expect(await screen.findByText('version 3 already exists for strategy 42')).toBeTruthy();
    // the edit survives the failed submission
    expect(screen.getAllByLabelText('Left operand period')[0]).toHaveProperty('value', '99');
  });
});
