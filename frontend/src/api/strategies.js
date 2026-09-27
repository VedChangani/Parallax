import { request } from './httpClient.js';

/**
 * Strategy endpoint functions (D-31). Every function here is a thin
 * wrapper around the single fetch call site in httpClient.js — no
 * component ever calls request()/fetch() directly for a strategy resource.
 */

/**
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').StrategyResponse[]>}
 */
export function listStrategies(signal) {
  return request('/api/strategies', { signal });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').StrategyResponse>}
 */
export function getStrategy(id, signal) {
  return request(`/api/strategies/${id}`, { signal });
}

/**
 * @param {{name: string, description: string, definition: import('./types.js').StrategyDefinitionDto}} body
 * @returns {Promise<import('./types.js').StrategyResponse>}
 */
export function createStrategy(body) {
  return request('/api/strategies', { method: 'POST', json: body });
}

/**
 * @param {number} id
 * @param {{name: string, description: string}} body
 * @returns {Promise<import('./types.js').StrategyResponse>}
 */
export function updateStrategyMetadata(id, body) {
  return request(`/api/strategies/${id}`, { method: 'PATCH', json: body });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').StrategyVersionSummaryResponse[]>}
 */
export function listStrategyVersions(id, signal) {
  return request(`/api/strategies/${id}/versions`, { signal });
}

/**
 * @param {number} id
 * @param {number} version
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').StrategyVersionResponse>}
 */
export function getStrategyVersion(id, version, signal) {
  return request(`/api/strategies/${id}/versions/${version}`, { signal });
}

/**
 * The request body here is the bare {@link import('./types.js').StrategyDefinitionDto}
 * tree itself (`entryCondition`/`exitCondition`/`positionSizing`) — never
 * wrapped in an envelope with `name`/`description`, unlike
 * {@link createStrategy}. This mirrors the backend's
 * `POST /api/strategies/{id}/versions` handler exactly (StrategyController#createVersion).
 *
 * @param {number} id
 * @param {import('./types.js').StrategyDefinitionDto} definition
 * @returns {Promise<import('./types.js').StrategyVersionResponse>}
 */
export function createStrategyVersion(id, definition) {
  return request(`/api/strategies/${id}/versions`, { method: 'POST', json: definition });
}
