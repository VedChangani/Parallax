import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as immutableCache from '../../api/immutableCache.js';
import { DatasetListPage } from './DatasetListPage.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderList() {
  return render(
    <MemoryRouter initialEntries={['/datasets']}>
      <Routes>
        <Route path="/datasets" element={<DatasetListPage />} />
        <Route path="/datasets/:id" element={<p>market detail page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DatasetListPage (Markets)', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state before data arrives', () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {}));
    renderList();

    expect(screen.getByRole('heading', { name: 'Markets' })).toBeTruthy();
    expect(screen.queryByRole('searchbox')).toBeNull();
  });

  it('renders market data on success', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([
        { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 2, createdAt: '2024-01-01T00:00:00Z' },
      ]),
    );
    renderList();

    const link = await screen.findByRole('link', { name: /AAPL/ });
    expect(link.textContent).toContain('Apple Inc.');
    expect(link.textContent).toContain('Latest snapshot v2');
  });

  it('filters the list by symbol or display name', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([
        { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' },
        { id: 2, name: 'Reliance Industries', symbol: 'RELIANCE', latestVersionNumber: 3, createdAt: '2024-01-01T00:00:00Z' },
      ]),
    );
    renderList();

    await screen.findByRole('link', { name: /AAPL/ });
    expect(screen.getByRole('link', { name: /RELIANCE/ })).toBeTruthy();

    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'reliance' } });

    expect(screen.queryByRole('link', { name: /AAPL/ })).toBeNull();
    expect(screen.getByRole('link', { name: /RELIANCE/ })).toBeTruthy();
  });

  it('shows a dedicated message when the search matches nothing', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([{ id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' }]),
    );
    renderList();

    await screen.findByRole('link', { name: /AAPL/ });
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'zzz' } });

    expect(await screen.findByText('No markets match your search.')).toBeTruthy();
  });

  it('shows an inviting empty state with no markets yet', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([]));
    renderList();

    expect(await screen.findByText('No markets yet')).toBeTruthy();
    expect(screen.getByText(/begin researching strategies/i)).toBeTruthy();
    expect(screen.getByText(/preserve an immutable snapshot/i)).toBeTruthy();
  });

  it('shows an error state on failure, and recovers via retry', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500))
      .mockResolvedValueOnce(jsonResponse([]));
    renderList();

    expect(await screen.findByRole('alert')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('No markets yet')).toBeTruthy();
  });

  it('navigates to the market detail page from the list', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([
        { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' },
      ]),
    );
    renderList();

    const link = await screen.findByRole('link', { name: /AAPL/ });
    fireEvent.click(link);

    expect(await screen.findByText('market detail page')).toBeTruthy();
  });

  it('navigates to the newly created market after a successful "Add market first" creation', async () => {
    globalThis.fetch = vi.fn((url, init) => {
      if (init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse(
            { id: 9, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' },
            201,
          ),
        );
      }
      return Promise.resolve(jsonResponse([]));
    });
    renderList();

    await screen.findByText('No markets yet');
    fireEvent.click(screen.getAllByRole('button', { name: 'Add market' })[0]);

    const panel = within(screen.getByRole('heading', { name: 'Add market' }).closest('div'));
    fireEvent.click(panel.getByLabelText('Add market first'));
    fireEvent.change(panel.getByLabelText('Symbol'), { target: { value: 'AAPL' } });
    fireEvent.change(panel.getByLabelText('Display name'), { target: { value: 'Apple Inc.' } });
    fireEvent.click(panel.getByRole('button', { name: 'Add market' }));

    expect(await screen.findByText('market detail page')).toBeTruthy();
  });

  it('never uses the immutable cache for the market list (mutable resource)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([]));
    renderList();

    await screen.findByText('No markets yet');
    expect(immutableCache.has('/api/datasets')).toBe(false);
  });
});
