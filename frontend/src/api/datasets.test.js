import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createDataset,
  getDataset,
  getDatasetBars,
  getDatasetVersion,
  importAlphaVantage,
  listDatasetVersions,
  listDatasets,
  uploadCsv,
} from './datasets.js';

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

describe('datasets api', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn().mockResolvedValue(jsonResponse({}));
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('listDatasets calls GET /api/datasets', async () => {
    await listDatasets();
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets');
    expect(init.method).toBe('GET');
  });

  it('getDataset calls GET /api/datasets/{id}', async () => {
    await getDataset(7);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/datasets/7');
  });

  it('createDataset posts the exact name/symbol JSON body', async () => {
    await createDataset({ name: 'Apple daily', symbol: 'AAPL' });
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ name: 'Apple daily', symbol: 'AAPL' });
  });

  it('listDatasetVersions calls GET /api/datasets/{id}/versions', async () => {
    await listDatasetVersions(7);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/datasets/7/versions');
  });

  it('getDatasetVersion calls GET /api/datasets/{id}/versions/{version}', async () => {
    await getDatasetVersion(7, 2);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/datasets/7/versions/2');
  });

  it('getDatasetBars calls GET /api/datasets/{id}/versions/{version}/bars', async () => {
    await getDatasetBars(7, 2);
    expect(globalThis.fetch.mock.calls[0][0]).toBe('/api/datasets/7/versions/2/bars');
  });

  it('uploadCsv posts a FormData body with exactly file and adjustmentBasis', async () => {
    const file = new File(['date,open,high,low,close,volume'], 'data.csv', { type: 'text/csv' });
    await uploadCsv(7, file, 'RAW');

    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets/7/versions');
    expect(init.method).toBe('POST');
    expect(init.headers['Content-Type']).toBeUndefined();

    const formData = init.body;
    expect(formData).toBeInstanceOf(FormData);
    expect([...formData.keys()]).toEqual(['file', 'adjustmentBasis']);
    expect(formData.get('file')).toBe(file);
    expect(formData.get('adjustmentBasis')).toBe('RAW');
  });

  it('importAlphaVantage posts the exact historyDepth JSON body for COMPACT', async () => {
    await importAlphaVantage(7, 'COMPACT');
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).toBe('/api/datasets/7/versions/alpha-vantage');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ historyDepth: 'COMPACT' });
  });

  it('importAlphaVantage posts the exact historyDepth JSON body for FULL', async () => {
    await importAlphaVantage(7, 'FULL');
    const [, init] = globalThis.fetch.mock.calls[0];
    expect(JSON.parse(init.body)).toEqual({ historyDepth: 'FULL' });
  });

  it('never includes an API key anywhere in the Alpha Vantage request', async () => {
    await importAlphaVantage(7, 'COMPACT');
    const [path, init] = globalThis.fetch.mock.calls[0];
    expect(path).not.toMatch(/key/i);
    expect(JSON.parse(init.body)).not.toHaveProperty('apiKey');
  });
});
