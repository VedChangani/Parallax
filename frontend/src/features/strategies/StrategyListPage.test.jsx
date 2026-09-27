import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrategyListPage } from './StrategyListPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

const strategies = [
  { id: 1, name: 'Momentum Cross', description: 'SMA trend-following strategy', latestVersionNumber: 4, createdAt: '2024-01-01T00:00:00Z' },
  { id: 2, name: 'Mean Reversion', description: 'RSI-based pullback strategy', latestVersionNumber: 2, createdAt: '2024-02-01T00:00:00Z' },
];

function renderList() {
  return render(
    <MemoryRouter initialEntries={['/strategies']}>
      <Routes>
        <Route path="/strategies" element={<StrategyListPage />} />
        <Route path="/strategies/new" element={<p>new strategy page</p>} />
        <Route path="/strategies/:id" element={<p>strategy detail page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('StrategyListPage', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse(strategies));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists every strategy with its latest version and created date', async () => {
    renderList();

    expect(await screen.findByText('Momentum Cross')).toBeTruthy();
    expect(screen.getByText('Mean Reversion')).toBeTruthy();
    expect(screen.getByText('Latest v4')).toBeTruthy();
    expect(screen.getByText('Latest v2')).toBeTruthy();
  });

  it('filters the list by name', async () => {
    renderList();
    await screen.findByText('Momentum Cross');

    fireEvent.change(screen.getByLabelText('Search strategies'), { target: { value: 'momentum' } });

    expect(screen.getByText('Momentum Cross')).toBeTruthy();
    expect(screen.queryByText('Mean Reversion')).toBeNull();
  });

  it('filters the list by description', async () => {
    renderList();
    await screen.findByText('Momentum Cross');

    fireEvent.change(screen.getByLabelText('Search strategies'), { target: { value: 'pullback' } });

    expect(screen.getByText('Mean Reversion')).toBeTruthy();
    expect(screen.queryByText('Momentum Cross')).toBeNull();
  });

  it('shows an empty-results message when nothing matches the search', async () => {
    renderList();
    await screen.findByText('Momentum Cross');

    fireEvent.change(screen.getByLabelText('Search strategies'), { target: { value: 'nonexistent' } });

    expect(screen.getByText('No strategies match your search.')).toBeTruthy();
  });

  it('shows an empty state with a call to action when there are no strategies at all', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([]));
    renderList();

    expect(await screen.findByText('No strategies yet')).toBeTruthy();
  });

  it('shows an error state with retry on a failed fetch', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({ title: 'Internal error', detail: 'boom' }, 500));
    renderList();

    expect(await screen.findByText('Could not load strategies')).toBeTruthy();
    expect(screen.getByText('boom')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeTruthy();
  });

  it('navigates to /strategies/new from the primary action', async () => {
    renderList();
    await screen.findByText('Momentum Cross');

    fireEvent.click(screen.getByRole('link', { name: 'New strategy' }));

    expect(await screen.findByText('new strategy page')).toBeTruthy();
  });

  it('opens a strategy detail page', async () => {
    renderList();
    await screen.findByText('Momentum Cross');

    fireEvent.click(screen.getAllByText('Open →')[0]);

    expect(await screen.findByText('strategy detail page')).toBeTruthy();
  });
});
