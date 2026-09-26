import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DatasetCreateForm } from './DatasetCreateForm.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function fillFields({ symbol, name }) {
  if (symbol !== undefined) {
    fireEvent.change(screen.getByLabelText('Symbol'), { target: { value: symbol } });
  }
  if (name !== undefined) {
    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: name } });
  }
}

function chooseAddMarketFirst() {
  fireEvent.click(screen.getByLabelText('Add market first'));
}

function submit() {
  fireEvent.click(screen.getByRole('button', { name: /add market/i }));
}

describe('DatasetCreateForm', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires a display name and a symbol before submitting', () => {
    const onCreated = vi.fn();
    render(<DatasetCreateForm onCreated={onCreated} />);

    submit();

    expect(screen.getByText('Display name is required.')).toBeTruthy();
    expect(screen.getByText('Symbol is required.')).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('rejects a symbol that does not match the D-32 grammar', () => {
    render(<DatasetCreateForm onCreated={vi.fn()} />);
    fillFields({ symbol: 'aapl!', name: 'Apple Inc.' });
    submit();

    expect(
      screen.getByText('Symbol must be uppercase letters/digits, and may include . _ - (max 32 characters).'),
    ).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('posts a valid submission (add market first) and reports the created market', async () => {
    const created = { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' };
    globalThis.fetch.mockResolvedValue(jsonResponse(created, 201));
    const onCreated = vi.fn();

    render(<DatasetCreateForm onCreated={onCreated} />);
    chooseAddMarketFirst();
    fillFields({ symbol: 'AAPL', name: 'Apple Inc.' });
    submit();

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created, {}));

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets');
    expect(JSON.parse(init.body)).toEqual({ name: 'Apple Inc.', symbol: 'AAPL' });
  });

  it('defaults to loading from Alpha Vantage immediately after creation', async () => {
    const created = { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' };
    const version = { datasetId: 1, versionNumber: 1, barCount: 100 };
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse(created, 201))
      .mockResolvedValueOnce(jsonResponse(version, 201));
    const onCreated = vi.fn();

    render(<DatasetCreateForm onCreated={onCreated} />);
    fillFields({ symbol: 'AAPL', name: 'Apple Inc.' });
    submit();

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created, {}));

    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
    const [avPath, avInit] = globalThis.fetch.mock.calls[1];
    expect(avPath).toBe('/api/datasets/1/versions/alpha-vantage');
    expect(JSON.parse(avInit.body)).toEqual({ historyDepth: 'COMPACT' });
  });

  it('sends FULL history depth when selected', async () => {
    const created = { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' };
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse(created, 201))
      .mockResolvedValueOnce(jsonResponse({ datasetId: 1, versionNumber: 1, barCount: 5000 }, 201));

    render(<DatasetCreateForm onCreated={vi.fn()} />);
    fillFields({ symbol: 'AAPL', name: 'Apple Inc.' });
    fireEvent.change(screen.getByLabelText('History'), { target: { value: 'FULL' } });
    submit();

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(2));
    const [, avInit] = globalThis.fetch.mock.calls[1];
    expect(JSON.parse(avInit.body)).toEqual({ historyDepth: 'FULL' });
  });

  it('still reports the created market when the automatic Alpha Vantage load fails', async () => {
    const created = { id: 1, name: 'Apple Inc.', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' };
    globalThis.fetch
      .mockResolvedValueOnce(jsonResponse(created, 201))
      .mockResolvedValueOnce(jsonResponse({ title: 'Market data unavailable', detail: 'rate limit exceeded' }, 503));
    const onCreated = vi.fn();

    render(<DatasetCreateForm onCreated={onCreated} />);
    fillFields({ symbol: 'AAPL', name: 'Apple Inc.' });
    submit();

    await waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith(created, { importError: 'rate limit exceeded' }),
    );
  });

  it('maps a backend field error onto the offending field', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse(
        {
          title: 'Malformed request',
          detail: 'request failed validation',
          errors: [{ field: 'symbol', message: 'must match "^[A-Z0-9][A-Z0-9._-]{0,31}$"' }],
        },
        400,
      ),
    );
    render(<DatasetCreateForm onCreated={vi.fn()} />);
    chooseAddMarketFirst();
    fillFields({ symbol: 'AAPL', name: 'Apple Inc.' });
    submit();

    expect(await screen.findByText('must match "^[A-Z0-9][A-Z0-9._-]{0,31}$"')).toBeTruthy();
  });

  it('shows a server-curated message for a non-field failure (e.g. a duplicate name conflict)', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Conflict', detail: 'a market named "Apple Inc." already exists' }, 409),
    );
    render(<DatasetCreateForm onCreated={vi.fn()} />);
    chooseAddMarketFirst();
    fillFields({ symbol: 'AAPL', name: 'Apple Inc.' });
    submit();

    expect(await screen.findByText('a market named "Apple Inc." already exists')).toBeTruthy();
  });
});
