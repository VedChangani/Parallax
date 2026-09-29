/**
 * Pure, plain-language explanations for a `—` in the performance summary
 * (I-3, Phase 10 Batch 4) — restating the engine's own documented emptiness
 * conditions (D-26, architecture.md §12) from data already on the page.
 * Never recomputes a metric: CAGR's rule has exactly one cause, so
 * `metrics.cagr === null` always explains itself the same way; the others
 * are distinguished only by data already fetched for another reason (the
 * metrics response itself, and the equity curve's own length). Each function
 * returns `undefined` when the metric is present (no explanation needed) or
 * when the cause cannot be determined from what is already known.
 */

/**
 * CAGR (D-26 §CAGR) has exactly one emptiness condition: an observed span
 * under 365 days. There is no other cause, so this is always exact.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @returns {string | undefined}
 */
export function cagrExplanation(metrics) {
  if (metrics.cagr !== null) return undefined;
  return 'This run spans less than 365 days, so CAGR is not shown (V1 never annualizes a sub-year return).';
}

/**
 * Volatility (D-26 §Volatility) is empty for exactly one reason: fewer than
 * two periodic returns. `returnCount`, when supplied, is the equity curve's
 * own point count minus one (a structural count, not a recomputed
 * statistic) — used only to state the exact number, never to decide whether
 * to show an explanation at all.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @param {number | undefined} returnCount
 * @returns {string | undefined}
 */
export function volatilityExplanation(metrics, returnCount) {
  if (metrics.volatility !== null) return undefined;
  return returnCount !== undefined
    ? `Fewer than two return observations (${returnCount}) were recorded for this run.`
    : 'Fewer than two return observations were recorded for this run.';
}

/**
 * Sharpe ratio (D-26 §Sharpe) is empty for one of two reasons: fewer than
 * two returns, or exactly zero volatility (every return identical,
 * including a flat no-trade run). `metrics.volatility` is already-persisted
 * data, not a recomputation, so checking it against `0` here only reads an
 * existing value.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @param {number | undefined} returnCount
 * @returns {string | undefined}
 */
export function sharpeExplanation(metrics, returnCount) {
  if (metrics.sharpeRatio !== null) return undefined;
  if (returnCount !== undefined && returnCount < 2) {
    return `Fewer than two return observations (${returnCount}) were recorded for this run.`;
  }
  if (metrics.volatility === 0) {
    return 'Every return in this run was identical, so volatility is zero and the ratio is undefined.';
  }
  return 'Fewer than two return observations were recorded, or every return was identical.';
}

/**
 * Win rate (D-26 §Trade statistics) is empty iff there were no closed
 * trades at all.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @returns {string | undefined}
 */
export function winRateExplanation(metrics) {
  if (metrics.winRate !== null) return undefined;
  return 'No trades were closed in this run.';
}

/**
 * Average win is empty when there were no closed trades at all, or when
 * there were closed trades but none of them was a win.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @returns {string | undefined}
 */
export function averageWinExplanation(metrics) {
  if (metrics.averageWin !== null) return undefined;
  return metrics.closedTradeCount === 0 ? 'No trades were closed in this run.' : 'No winning trades in this run.';
}

/**
 * Average loss is empty when there were no closed trades at all, or when
 * there were closed trades but none of them was a loss.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @returns {string | undefined}
 */
export function averageLossExplanation(metrics) {
  if (metrics.averageLoss !== null) return undefined;
  return metrics.closedTradeCount === 0 ? 'No trades were closed in this run.' : 'No losing trades in this run.';
}

/**
 * Profit factor (D-41) is empty exactly when no closed trade lost money
 * (no closed trades, or only winning/breakeven ones): its denominator, the
 * absolute gross loss, would be zero. Never shown as infinity.
 *
 * @param {import('../../api/types.js').PerformanceMetricsResponse} metrics
 * @returns {string | undefined}
 */
export function profitFactorExplanation(metrics) {
  if (metrics.profitFactor !== null) return undefined;
  return 'Needs at least one losing closed trade.';
}
