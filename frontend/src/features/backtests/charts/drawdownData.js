/**
 * Turns the equity-curve response's per-point `drawdown` (a fraction >= 0
 * below the running peak, from the engine's own drawdown series, D-41) into
 * the arrays the drawdown chart plots. Kept as its own pure function so the
 * date alignment and the sign convention are testable without a canvas.
 *
 * Presentation-only conversion, like the equity chart's `Number(point.equity)`:
 * the fraction becomes a percentage and is negated, so `0` means "at a peak"
 * and negative values mean "in drawdown". Points without a finite numeric
 * `drawdown` are skipped rather than plotted as a bogus zero; dates stay
 * exactly the equity points' own dates, in order.
 *
 * @param {import('../../../api/types.js').BacktestEquityPointResponse[]} points
 * @returns {{dates: string[], xs: number[], ys: number[], deepestIndex: number}}
 *   `deepestIndex` is the index into `dates`/`ys` of the largest drawdown, or `-1` when empty
 */
export function drawdownChartData(points) {
  const dates = [];
  const xs = [];
  const ys = [];
  let deepestIndex = -1;

  for (const point of points) {
    if (typeof point.drawdown !== 'number' || !Number.isFinite(point.drawdown)) continue;
    const y = point.drawdown === 0 ? 0 : -point.drawdown * 100;
    if (deepestIndex === -1 || y < ys[deepestIndex]) deepestIndex = ys.length;
    dates.push(point.date);
    xs.push(Date.parse(`${point.date}T00:00:00Z`) / 1000);
    ys.push(y);
  }

  return { dates, xs, ys, deepestIndex };
}
