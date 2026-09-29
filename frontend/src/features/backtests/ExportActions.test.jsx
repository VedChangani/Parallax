import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadTextFile } from '../../lib/download.js';
import { ExportActions } from './ExportActions.jsx';

vi.mock('../../lib/download.js', () => ({ downloadTextFile: vi.fn() }));

function csvResponse(text, status = 200) {
  return new Response(text, { status, headers: { 'content-type': 'text/csv;charset=UTF-8' } });
}

function problem(status) {
  return new Response(JSON.stringify({ title: 'Problem', detail: 'something went wrong' }), {
    status,
    headers: { 'content-type': 'application/problem+json' },
  });
}

describe('ExportActions', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
    downloadTextFile.mockClear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('offers both exports as low-emphasis actions in a labelled group', () => {
    render(<ExportActions runId={7} />);

    expect(screen.getByRole('group', { name: 'Export results' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Export equity CSV' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Export trades CSV' })).toBeTruthy();
    // Nothing is fetched or downloaded until the user asks.
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('downloads the equity CSV exactly as the backend returned it', async () => {
    const body = 'date,equity,drawdown\r\n2024-01-02,10000,0\r\n';
    globalThis.fetch.mockResolvedValue(csvResponse(body));
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export equity CSV' }));

    await waitFor(() => expect(downloadTextFile).toHaveBeenCalledTimes(1));
    expect(downloadTextFile).toHaveBeenCalledWith('backtest-7-equity-curve.csv', body);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/7/equity-curve.csv');
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('downloads the trades CSV under its own filename', async () => {
    globalThis.fetch.mockResolvedValue(csvResponse('status\r\n'));
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export trades CSV' }));

    await waitFor(() => expect(downloadTextFile).toHaveBeenCalledWith('backtest-7-trades.csv', 'status\r\n'));
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/backtest-runs/7/trades.csv');
  });

  it('never calls anything but the two export endpoints (no re-run, no other request)', async () => {
    globalThis.fetch.mockImplementation(() => Promise.resolve(csvResponse('x')));
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export equity CSV' }));
    await waitFor(() => expect(downloadTextFile).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: 'Export trades CSV' }));
    await waitFor(() => expect(downloadTextFile).toHaveBeenCalledTimes(2));

    expect(globalThis.fetch.mock.calls.map(([path]) => path)).toEqual([
      '/api/backtest-runs/7/equity-curve.csv',
      '/api/backtest-runs/7/trades.csv',
    ]);
    expect(globalThis.fetch.mock.calls.every(([, init]) => init.method === 'GET')).toBe(true);
  });

  it('disables both buttons while an export is in flight', async () => {
    let resolveFetch;
    globalThis.fetch.mockReturnValue(new Promise((resolve) => { resolveFetch = resolve; }));
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export equity CSV' }));

    expect(screen.getByRole('button', { name: 'Export equity CSV' }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: 'Export trades CSV' }).disabled).toBe(true);

    resolveFetch(csvResponse('x'));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Export equity CSV' }).disabled).toBe(false));
  });

  it('shows an inline error and downloads nothing when the export fails', async () => {
    globalThis.fetch.mockResolvedValue(problem(500));
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export equity CSV' }));

    expect((await screen.findByRole('alert')).textContent).toMatch(/Export failed/);
    expect(downloadTextFile).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Export equity CSV' }).disabled).toBe(false);
  });

  it('shows an inline error on a network failure and clears it after a successful retry', async () => {
    globalThis.fetch.mockRejectedValueOnce(new TypeError('offline'));
    globalThis.fetch.mockResolvedValueOnce(csvResponse('ok'));
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export trades CSV' }));
    expect(await screen.findByRole('alert')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Export trades CSV' }));
    await waitFor(() => expect(downloadTextFile).toHaveBeenCalledWith('backtest-7-trades.csv', 'ok'));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('reports a failure to start the download itself', async () => {
    globalThis.fetch.mockResolvedValue(csvResponse('x'));
    downloadTextFile.mockImplementationOnce(() => {
      throw new Error('blocked by the browser');
    });
    render(<ExportActions runId={7} />);

    fireEvent.click(screen.getByRole('button', { name: 'Export equity CSV' }));

    expect((await screen.findByRole('alert')).textContent).toContain('blocked by the browser');
  });
});
