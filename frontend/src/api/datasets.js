import { request } from './httpClient.js';

export function listDatasets(signal) {
  return request('/api/datasets', { signal });
}

export function getDataset(id, signal) {
  return request(`/api/datasets/${id}`, { signal });
}

export function createDataset(body) {
  return request('/api/datasets', { method: 'POST', json: body });
}

export function listDatasetVersions(id, signal) {
  return request(`/api/datasets/${id}/versions`, { signal });
}

export function getDatasetVersion(id, version, signal) {
  return request(`/api/datasets/${id}/versions/${version}`, { signal });
}

export function getDatasetBars(id, version, signal) {
  return request(`/api/datasets/${id}/versions/${version}/bars`, { signal });
}

export function uploadCsv(id, file, adjustmentBasis) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('adjustmentBasis', adjustmentBasis);
  return request(`/api/datasets/${id}/versions`, { method: 'POST', formData });
}

export function importAlphaVantage(id, historyDepth) {
  return request(`/api/datasets/${id}/versions/alpha-vantage`, {
    method: 'POST',
    json: { historyDepth },
  });
}
