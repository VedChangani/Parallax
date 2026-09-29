import { request } from './httpClient.js';

export function listBacktestRuns(signal) {
  return request('/api/backtest-runs', { signal });
}

export function getBacktestRun(id, signal) {
  return request(`/api/backtest-runs/${id}`, { signal });
}

export function createBacktestRun(body) {
  return request('/api/backtest-runs', { method: 'POST', json: body });
}

export function getBacktestEquity(id, signal) {
  return request(`/api/backtest-runs/${id}/equity-curve`, { signal });
}

export function getBacktestEquityCsv(id, signal) {
  return request(`/api/backtest-runs/${id}/equity-curve.csv`, { signal, responseType: 'text' });
}

export function getBacktestTradesCsv(id, signal) {
  return request(`/api/backtest-runs/${id}/trades.csv`, { signal, responseType: 'text' });
}

export function getBacktestTrades(id, signal) {
  return request(`/api/backtest-runs/${id}/trades`, { signal });
}

export function getBacktestRejections(id, signal) {
  return request(`/api/backtest-runs/${id}/rejections`, { signal });
}
