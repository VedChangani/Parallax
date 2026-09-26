import { request } from './httpClient.js';

/**
 * Dataset endpoint functions (D-32/D-33). Every function here is a thin
 * wrapper around the single fetch call site in httpClient.js — no
 * component ever calls request()/fetch() directly for a dataset resource.
 */

/**
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').DatasetResponse[]>}
 */
export function listDatasets(signal) {
  return request('/api/datasets', { signal });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').DatasetResponse>}
 */
export function getDataset(id, signal) {
  return request(`/api/datasets/${id}`, { signal });
}

/**
 * @param {{name: string, symbol: string}} body
 * @returns {Promise<import('./types.js').DatasetResponse>}
 */
export function createDataset(body) {
  return request('/api/datasets', { method: 'POST', json: body });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').DatasetVersionResponse[]>}
 */
export function listDatasetVersions(id, signal) {
  return request(`/api/datasets/${id}/versions`, { signal });
}

/**
 * @param {number} id
 * @param {number} version
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').DatasetVersionResponse>}
 */
export function getDatasetVersion(id, version, signal) {
  return request(`/api/datasets/${id}/versions/${version}`, { signal });
}

/**
 * @param {number} id
 * @param {number} version
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').DatasetBarsResponse>}
 */
export function getDatasetBars(id, version, signal) {
  return request(`/api/datasets/${id}/versions/${version}/bars`, { signal });
}

/**
 * @param {number} id
 * @param {File} file
 * @param {import('./types.js').AdjustmentBasis} adjustmentBasis
 * @returns {Promise<import('./types.js').DatasetVersionResponse>}
 */
export function uploadCsv(id, file, adjustmentBasis) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('adjustmentBasis', adjustmentBasis);
  return request(`/api/datasets/${id}/versions`, { method: 'POST', formData });
}

/**
 * @param {number} id
 * @param {'COMPACT' | 'FULL'} historyDepth
 * @returns {Promise<import('./types.js').DatasetVersionResponse>}
 */
export function importAlphaVantage(id, historyDepth) {
  return request(`/api/datasets/${id}/versions/alpha-vantage`, {
    method: 'POST',
    json: { historyDepth },
  });
}
