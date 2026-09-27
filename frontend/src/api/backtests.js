import { request } from './httpClient.js';

/**
 * Backtest run endpoint functions (D-34). Every function here is a thin
 * wrapper around the single fetch call site in httpClient.js — no
 * component ever calls request()/fetch() directly for a backtest-run
 * resource.
 */

/**
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').BacktestRunSummaryResponse[]>}
 */
export function listBacktestRuns(signal) {
  return request('/api/backtest-runs', { signal });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').BacktestRunResponse>}
 */
export function getBacktestRun(id, signal) {
  return request(`/api/backtest-runs/${id}`, { signal });
}

/**
 * Creates and synchronously executes a new backtest run (`POST
 * /api/backtest-runs`). There is no job id and no polling — the backend
 * runs the backtest to completion before responding with the full,
 * persisted {@link import('./types.js').BacktestRunResponse}.
 *
 * @param {import('./types.js').CreateBacktestRunRequest} body
 * @returns {Promise<import('./types.js').BacktestRunResponse>}
 */
export function createBacktestRun(body) {
  return request('/api/backtest-runs', { method: 'POST', json: body });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').BacktestEquityPointResponse[]>}
 */
export function getBacktestEquity(id, signal) {
  return request(`/api/backtest-runs/${id}/equity-curve`, { signal });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').BacktestTradeResponse[]>}
 */
export function getBacktestTrades(id, signal) {
  return request(`/api/backtest-runs/${id}/trades`, { signal });
}

/**
 * @param {number} id
 * @param {AbortSignal} [signal]
 * @returns {Promise<import('./types.js').BacktestRejectionResponse[]>}
 */
export function getBacktestRejections(id, signal) {
  return request(`/api/backtest-runs/${id}/rejections`, { signal });
}
