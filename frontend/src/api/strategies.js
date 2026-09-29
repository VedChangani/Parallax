import { request } from './httpClient.js';

export function listStrategies(signal) {
  return request('/api/strategies', { signal });
}

export function getStrategy(id, signal) {
  return request(`/api/strategies/${id}`, { signal });
}

export function createStrategy(body) {
  return request('/api/strategies', { method: 'POST', json: body });
}

export function updateStrategyMetadata(id, body) {
  return request(`/api/strategies/${id}`, { method: 'PATCH', json: body });
}

export function listStrategyVersions(id, signal) {
  return request(`/api/strategies/${id}/versions`, { signal });
}

export function getStrategyVersion(id, version, signal) {
  return request(`/api/strategies/${id}/versions/${version}`, { signal });
}

export function createStrategyVersion(id, definition) {
  return request(`/api/strategies/${id}/versions`, { method: 'POST', json: definition });
}
