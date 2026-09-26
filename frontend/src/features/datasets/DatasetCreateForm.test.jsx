import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DatasetCreateForm } from './DatasetCreateForm.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function fillAndSubmit({ name, symbol }) {
  if (name !== undefined) {
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: name } });
  }
  if (symbol !== undefined) {
    fireEvent.change(screen.getByLabelText('Symbol'), { target: { value: symbol } });
  }
  fireEvent.click(screen.getByRole('button', { name: /create dataset/i }));
}

describe('DatasetCreateForm', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires a name and a symbol before submitting', () => {
    const onCreated = vi.fn();
    render(<DatasetCreateForm onCreated={onCreated} />);

    fireEvent.click(screen.getByRole('button', { name: /create dataset/i }));

    expect(screen.getByText('Name is required.')).toBeTruthy();
    expect(screen.getByText('Symbol is required.')).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it('rejects a symbol that does not match the D-32 grammar', () => {
    render(<DatasetCreateForm onCreated={vi.fn()} />);
    fillAndSubmit({ name: 'Apple daily', symbol: 'aapl!' });

    expect(
      screen.getByText('Symbol must be uppercase letters/digits, and may include . _ - (max 32 characters).'),
    ).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('posts a valid submission and reports the created dataset', async () => {
    const created = { id: 1, name: 'Apple daily', symbol: 'AAPL', latestVersionNumber: 0, createdAt: '2024-01-01T00:00:00Z' };
    globalThis.fetch.mockResolvedValue(jsonResponse(created, 201));
    const onCreated = vi.fn();

    render(<DatasetCreateForm onCreated={onCreated} />);
    fillAndSubmit({ name: 'Apple daily', symbol: 'AAPL' });

    await waitFor(() => expect(onCreated).toHaveBeenCalledWith(created));

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets');
    expect(JSON.parse(init.body)).toEqual({ name: 'Apple daily', symbol: 'AAPL' });
  });

  it('maps a backend field error onto the offending field', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse(
        { title: 'Malformed request', detail: 'request failed validation', errors: [{ field: 'symbol', message: 'must match "^[A-Z0-9][A-Z0-9._-]{0,31}$"' }] },
        400,
      ),
    );
    render(<DatasetCreateForm onCreated={vi.fn()} />);
    fillAndSubmit({ name: 'Apple daily', symbol: 'AAPL' });

    expect(await screen.findByText('must match "^[A-Z0-9][A-Z0-9._-]{0,31}$"')).toBeTruthy();
  });

  it('shows a server-curated message for a non-field failure (e.g. a duplicate name conflict)', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ title: 'Conflict', detail: 'a dataset named "Apple daily" already exists' }, 409));
    render(<DatasetCreateForm onCreated={vi.fn()} />);
    fillAndSubmit({ name: 'Apple daily', symbol: 'AAPL' });

    expect(await screen.findByText('a dataset named "Apple daily" already exists')).toBeTruthy();
  });
});
