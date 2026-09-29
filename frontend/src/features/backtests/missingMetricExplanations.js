export function cagrExplanation(metrics) {
  if (metrics.cagr !== null) return undefined;
  return 'This run spans less than 365 days, so CAGR is not shown (V1 never annualizes a sub-year return).';
}

export function volatilityExplanation(metrics, returnCount) {
  if (metrics.volatility !== null) return undefined;
  return returnCount !== undefined
    ? `Fewer than two return observations (${returnCount}) were recorded for this run.`
    : 'Fewer than two return observations were recorded for this run.';
}

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

export function winRateExplanation(metrics) {
  if (metrics.winRate !== null) return undefined;
  return 'No trades were closed in this run.';
}

export function averageWinExplanation(metrics) {
  if (metrics.averageWin !== null) return undefined;
  return metrics.closedTradeCount === 0 ? 'No trades were closed in this run.' : 'No winning trades in this run.';
}

export function averageLossExplanation(metrics) {
  if (metrics.averageLoss !== null) return undefined;
  return metrics.closedTradeCount === 0 ? 'No trades were closed in this run.' : 'No losing trades in this run.';
}

export function profitFactorExplanation(metrics) {
  if (metrics.profitFactor !== null) return undefined;
  return 'Needs at least one losing closed trade.';
}
