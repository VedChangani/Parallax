import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BacktestHistoryPage } from './BacktestHistoryPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

// Deliberately NOT in createdAt order relative to id, so a passing "sorted
// newest first" test proves the page sorts by createdAt - it does not just
// reverse whatever array order the backend happened to send. IDs/versions
// are chosen so no field's digits are an accidental substring of another
// run's field, keeping the search tests unambiguous.
const RUN_A = {
  id: 101, strategyId: 501, strategyVersion: 2, definitionHash: 'a'.repeat(64),
  datasetId: 701, datasetVersion: 3, contentHash: 'b'.repeat(64), engineSemanticsVersion: 1,
  createdAt: '2024-01-01T00:00:00Z',
};
const RUN_B = {
  id: 102, strategyId: 502, strategyVersion: 1, definitionHash: 'c'.repeat(64),
  datasetId: 702, datasetVersion: 1, contentHash: 'd'.repeat(64), engineSemanticsVersion: 1,
  createdAt: '2024-06-15T00:00:00Z',
};
const RUN_C = {
  id: 103, strategyId: 501, strategyVersion: 3, definitionHash: 'e'.repeat(64),
  datasetId: 701, datasetVersion: 4, contentHash: 'f'.repeat(64), engineSemanticsVersion: 2,
  createdAt: '2024-03-10T00:00:00Z',
};

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

  it('lists real runs sorted newest-first by createdAt, not backend array order', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
    renderPage();

    const rows = await screen.findAllByRole('row');
    const dataRows = rows.slice(1); // skip the header row
    expect(within(dataRows[0]).getByText('#102')).toBeTruthy(); // June - newest
    expect(within(dataRows[1]).getByText('#103')).toBeTruthy(); // March
    expect(within(dataRows[2]).getByText('#101')).toBeTruthy(); // January - oldest
  });

  it('makes only the list request - no per-run detail fetch (no N+1)', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
    renderPage();
    await screen.findByText('#101');

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs');
  });

  it('shows honest run identity - IDs and version references, never a fabricated name', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
    renderPage();

    const row = (await screen.findByText('#101')).closest('tr');
    expect(within(row).getByText('Market #701')).toBeTruthy();
    expect(within(row).getByText('Snapshot v3')).toBeTruthy();
    expect(within(row).getByText('Strategy #501')).toBeTruthy();
    expect(within(row).getByText('Version v2')).toBeTruthy();
    expect(within(row).getByText('2024-01-01')).toBeTruthy();

    fireEvent.click(within(row).getByText('Detail'));
    expect(within(row).getByText('engine v1')).toBeTruthy();
    expect(within(row).getByText(/strategy a{12}…/)).toBeTruthy();
    expect(within(row).getByText(/dataset b{12}…/)).toBeTruthy();
  });

  it('never shows a performance metric - the list response has none to show', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
    renderPage();
    await screen.findByText('#101');

    expect(screen.queryByText(/Total Return|CAGR|Sharpe|%/)).toBeNull();
  });

  it('shows an empty state with a primary action when there are no runs', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([])));
    renderPage();

    expect(await screen.findByText('No backtests yet')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: 'New backtest' }).length).toBeGreaterThan(0);
  });

  it('shows a safe generic error with retry, and reload succeeds', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500))
      .mockResolvedValueOnce(jsonResponse([RUN_A]));
    renderPage();

    expect(await screen.findByText('Could not load backtests')).toBeTruthy();
    expect(screen.getByText('an internal error occurred')).toBeTruthy();
    expect(screen.queryByText(/Exception|stack trace/i)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('#101')).toBeTruthy();
  });

  describe('search', () => {
    it('filters by run ID', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '102' } });
      expect(screen.getByText('#102')).toBeTruthy();
      expect(screen.queryByText('#101')).toBeNull();
      expect(screen.queryByText('#103')).toBeNull();
    });

    it('filters by strategy ID, matching every run that shares it', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '501' } });
      expect(screen.getByText('#101')).toBeTruthy();
      expect(screen.getByText('#103')).toBeTruthy();
      expect(screen.queryByText('#102')).toBeNull();
    });

    it('filters by dataset ID', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '702' } });
      expect(screen.getByText('#102')).toBeTruthy();
      expect(screen.queryByText('#101')).toBeNull();
      expect(screen.queryByText('#103')).toBeNull();
    });

    it('filters by dataset version', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '4' } });
      expect(screen.getByText('#103')).toBeTruthy();
      expect(screen.queryByText('#101')).toBeNull();
      expect(screen.queryByText('#102')).toBeNull();
    });

    it('shows a no-match message for a search that matches nothing, without treating it as an error', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A, RUN_B, RUN_C])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.change(screen.getByLabelText('Search backtests'), { target: { value: '999999' } });
      expect(screen.getByText('No runs match your search.')).toBeTruthy();
      expect(screen.queryByRole('alert')).toBeNull();
    });
  });

  describe('navigation', () => {
    it('links "New backtest" to /backtests/new', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.click(screen.getAllByRole('link', { name: 'New backtest' })[0]);
      expect(await screen.findByText('new backtest page')).toBeTruthy();
    });

    it('opens a run at /backtests/{runId}', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
      renderPage();
      await screen.findByText('#101');

      fireEvent.click(screen.getByRole('link', { name: 'Open run →' }));
      expect(await screen.findByText('run detail page')).toBeTruthy();
    });

    it('also opens a run by clicking its run number', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
      renderPage();

      fireEvent.click(await screen.findByText('#101'));
      expect(await screen.findByText('run detail page')).toBeTruthy();
    });
  });

  it('never shows an edit, delete, or re-run control - a run is an immutable historical record', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
    renderPage();
    await screen.findByText('#101');

    expect(screen.queryByRole('button', { name: /delete/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /edit/i })).toBeNull();
    expect(screen.queryByRole('link', { name: /re-?run/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /re-?run/i })).toBeNull();
  });

  describe('accessibility', () => {
    it('gives the search input an accessible label', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
      renderPage();
      expect(await screen.findByLabelText('Search backtests')).toBeTruthy();
    });

    it('gives the table real column headers', async () => {
      globalThis.fetch = vi.fn(() => Promise.resolve(jsonResponse([RUN_A])));
      renderPage();
      await screen.findByText('#101');

      expect(screen.getByRole('columnheader', { name: 'Run' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Market' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Strategy' })).toBeTruthy();
      expect(screen.getByRole('columnheader', { name: 'Created' })).toBeTruthy();
    });
  });
});
