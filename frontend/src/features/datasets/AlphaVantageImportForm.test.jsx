import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AlphaVantageImportForm } from './AlphaVantageImportForm.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderForm(onImported = vi.fn()) {
  render(
    <MemoryRouter>
      <AlphaVantageImportForm datasetId={7} onImported={onImported} />
    </MemoryRouter>,
  );
  return onImported;
}

describe('AlphaVantageImportForm', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('never exposes an API-key configuration field', () => {
    renderForm();
    expect(screen.queryByLabelText(/api.?key/i)).toBeNull();
    expect(screen.queryByPlaceholderText(/api.?key/i)).toBeNull();
  });

  it('defaults to and posts COMPACT with no API key in the request body', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ datasetId: 7, versionNumber: 1, barCount: 100 }, 201));
    renderForm();

    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    await screen.findByText(/Created version 1/);
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets/7/versions/alpha-vantage');
    const body = JSON.parse(init.body);
    expect(body).toEqual({ historyDepth: 'COMPACT' });
    expect(body).not.toHaveProperty('apiKey');
  });

  it('posts FULL when selected', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ datasetId: 7, versionNumber: 1, barCount: 5000 }, 201));
    renderForm();

    fireEvent.click(screen.getByLabelText(/full/i));
    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    await screen.findByText(/Created version 1/);
    const [, init] = globalThis.fetch.mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({ historyDepth: 'FULL' });
  });

  it('calls onImported with the created version on success', async () => {
    const version = { datasetId: 7, versionNumber: 4, barCount: 100 };
    globalThis.fetch.mockResolvedValue(jsonResponse(version, 201));
    const onImported = renderForm();

    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    await screen.findByText(/Created version 4/);
    expect(onImported).toHaveBeenCalledWith(version);
  });

  it('shows a clear message for a 422 provider rejection', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Market data request rejected', detail: 'unknown symbol' }, 422),
    );
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    expect(await screen.findByText('unknown symbol')).toBeTruthy();
  });

  it('shows a clear message for a 503 unavailable/rate-limit response', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Market data unavailable', detail: 'rate limit exceeded' }, 503),
    );
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    expect(await screen.findByText('rate limit exceeded')).toBeTruthy();
  });

  it('shows a clear message for a 502 transport/protocol failure', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Market data provider error', detail: 'malformed upstream response' }, 502),
    );
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    expect(await screen.findByText('malformed upstream response')).toBeTruthy();
  });

  it('shows a generic message for a 500 integrity failure, never internal details', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500),
    );
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: /import from alpha vantage/i }));

    expect(await screen.findByText('an internal error occurred')).toBeTruthy();
  });
});
