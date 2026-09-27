import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CsvUploadForm } from './CsvUploadForm.jsx';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function renderForm(onImported = vi.fn()) {
  render(
    <MemoryRouter>
      <CsvUploadForm datasetId={7} onImported={onImported} />
    </MemoryRouter>,
  );
  return onImported;
}

function selectFileAndBasis(
  file = new File(['date,open,high,low,close,volume\n2024-01-01,1,2,0.5,1.5,100'], 'data.csv', {
    type: 'text/csv',
  }),
) {
  fireEvent.change(screen.getByLabelText('CSV file'), { target: { files: [file] } });
  fireEvent.change(screen.getByLabelText('Adjustment basis'), { target: { value: 'RAW' } });
  return file;
}

describe('CsvUploadForm', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requires a file and an adjustment basis before submitting', () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    expect(screen.getByText('Choose a CSV file to upload.')).toBeTruthy();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('sends a FormData body with exactly file and adjustmentBasis', async () => {
    globalThis.fetch.mockResolvedValue(jsonResponse({ datasetId: 7, versionNumber: 1, barCount: 1 }, 201));
    renderForm();
    const file = selectFileAndBasis();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledTimes(1));

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets/7/versions');
    expect(init.headers['Content-Type']).toBeUndefined();
    const formData = init.body;
    expect([...formData.keys()]).toEqual(['file', 'adjustmentBasis']);
    expect(formData.get('file')).toBe(file);
    expect(formData.get('adjustmentBasis')).toBe('RAW');
  });

  it('reports the created version and calls onImported on success', async () => {
    const version = { datasetId: 7, versionNumber: 3, barCount: 42 };
    globalThis.fetch.mockResolvedValue(jsonResponse(version, 201));
    const onImported = renderForm();
    selectFileAndBasis();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    expect(await screen.findByText(/Created snapshot v3/)).toBeTruthy();
    expect(onImported).toHaveBeenCalledWith(version);
  });

  it('disables duplicate submission while an upload is in flight', async () => {
    let resolveFetch;
    globalThis.fetch.mockReturnValue(
      new Promise((resolve) => {
        resolveFetch = resolve;
      }),
    );
    renderForm();
    selectFileAndBasis();

    const button = screen.getByRole('button', { name: /import csv/i });
    fireEvent.click(button);
    expect(button.disabled).toBe(true);
    fireEvent.click(button);

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);

    resolveFetch(jsonResponse({ datasetId: 7, versionNumber: 1, barCount: 1 }, 201));
    await waitFor(() => expect(button.disabled).toBe(false));
  });

  it('shows a clear message for a 413 payload-too-large response', async () => {
    globalThis.fetch.mockResolvedValue(new Response(null, { status: 413 }));
    renderForm();
    selectFileAndBasis();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    expect(await screen.findByText('This CSV file is too large.')).toBeTruthy();
  });

  it('shows the backend detail for a 422 semantic validation failure', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Invalid dataset data', detail: 'line 3: close must be positive' }, 422),
    );
    renderForm();
    selectFileAndBasis();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    expect(await screen.findByText('line 3: close must be positive')).toBeTruthy();
  });

  it('shows a generic message for a 500 integrity failure, never internal details', async () => {
    globalThis.fetch.mockResolvedValue(
      jsonResponse({ title: 'Internal error', detail: 'an internal error occurred' }, 500),
    );
    renderForm();
    selectFileAndBasis();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    expect(await screen.findByText('an internal error occurred')).toBeTruthy();
  });

  it('shows a network-unavailable message when the backend cannot be reached', async () => {
    globalThis.fetch.mockRejectedValue(new TypeError('Failed to fetch'));
    renderForm();
    selectFileAndBasis();
    fireEvent.click(screen.getByRole('button', { name: /import csv/i }));

    expect(await screen.findByText(/could not reach the backend/i)).toBeTruthy();
  });
});
