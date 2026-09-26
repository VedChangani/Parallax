import { fireEvent, render, screen } from '@testing-library/react';
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
        <Route path="/datasets/:id" element={<p>dataset detail page</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('DatasetListPage', () => {
  beforeEach(() => {
    immutableCache.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows a loading state before data arrives', () => {
    globalThis.fetch = vi.fn(() => new Promise(() => {}));
    renderList();

    expect(screen.getByRole('heading', { name: 'Datasets' })).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
  });

  it('renders the dataset list on success', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([
        { id: 1, name: 'Apple daily', symbol: 'AAPL', latestVersionNumber: 2, createdAt: '2024-01-01T00:00:00Z' },
      ]),
    );
    renderList();

    expect(await screen.findByRole('link', { name: 'Apple daily' })).toBeTruthy();
    expect(screen.getByText('AAPL')).toBeTruthy();
    expect(screen.getByText('v2')).toBeTruthy();
  });

  it('shows an empty state with no datasets, explaining what a dataset is', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([]));
    renderList();

    expect(await screen.findByText('No datasets yet')).toBeTruthy();
    expect(screen.getByText(/versioned collection of daily bars/i)).toBeTruthy();
  });

  it('shows an error state on failure, and recovers via retry', async () => {
    globalThis.fetch = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500))
      .mockResolvedValueOnce(jsonResponse([]));
    renderList();

    expect(await screen.findByRole('alert')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByText('No datasets yet')).toBeTruthy();
  });

  it('navigates to the dataset detail page from the list', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse([
        { id: 1, name: 'Apple daily', symbol: 'AAPL', latestVersionNumber: 1, createdAt: '2024-01-01T00:00:00Z' },
      ]),
    );
    renderList();

    const link = await screen.findByRole('link', { name: 'Apple daily' });
    fireEvent.click(link);

    expect(await screen.findByText('dataset detail page')).toBeTruthy();
  });

  it('navigates to the newly created dataset after a successful inline creation', async () => {
    globalThis.fetch = vi.fn((url, init) => {
      if (init?.method === 'POST') {
        return Promise.resolve(
          jsonResponse(
            { id: 9, name: 'Apple daily', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' },
            201,
          ),
        );
      }
      return Promise.resolve(jsonResponse([]));
    });
    renderList();

    await screen.findByText('No datasets yet');
    fireEvent.click(screen.getByRole('button', { name: 'New dataset' }));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Apple daily' } });
    fireEvent.change(screen.getByLabelText('Symbol'), { target: { value: 'AAPL' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create dataset' }));

    expect(await screen.findByText('dataset detail page')).toBeTruthy();
  });

  it('never uses the immutable cache for the dataset list (mutable resource)', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse([]));
    renderList();

    await screen.findByText('No datasets yet');
    expect(immutableCache.has('/api/datasets')).toBe(false);
  });
});
