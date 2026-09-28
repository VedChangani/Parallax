import { formatMoney, formatPercent, formatRatio, formatSignedPercent, formatStatMoney } from '../../lib/format.js';
import {
  averageLossExplanation,
  averageWinExplanation,
  cagrExplanation,
  sharpeExplanation,
  volatilityExplanation,
  winRateExplanation,
} from './missingMetricExplanations.js';

const TONE_TEXT = { success: 'text-success', danger: 'text-danger' };

/**
 * @param {number | null | undefined} value
 * @returns {'success' | 'danger' | undefined}
 */
function returnTone(value) {
  if (value === null || value === undefined) return undefined;
  if (value > 0) return 'success';
  if (value < 0) return 'danger';
  return undefined;
}

/**
 * The primary/secondary performance-metric grid (D-34 Batch 5 §7): every
 * value is read directly off the persisted `PerformanceMetricsResponse` -
 * never recomputed, never re-derived from the equity curve or fills.
 * Semantic color is a secondary cue only; the sign/percentage text itself
 * always carries the meaning (CLAUDE.md §17/§24: never color alone).
 *
 * @param {object} props
 * @param {import('../../api/types.js').PerformanceMetricsResponse} props.metrics
 * @param {string} props.totalCommission
 * @param {string} props.totalSlippageCost
 * @param {number} [props.returnCount] - the equity curve's own point count
 *   minus one (I-3, Phase 10 Batch 4) - purely to explain a missing
 *   Sharpe/volatility with the exact observation count when known;
 *   `undefined` while the equity curve is still loading or failed to load
 *   simply omits that one detail, never blocking the rest of the summary.
 */
export function PerformanceSummary({ metrics, totalCommission, totalSlippageCost, returnCount }) {
  return (
    <div>
      <dl className="grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-5">
        <Metric label="Total return" value={formatSignedPercent(metrics.totalReturn)} tone={returnTone(metrics.totalReturn)} />
        <Metric label="CAGR" value={formatSignedPercent(metrics.cagr)} tone={returnTone(metrics.cagr)} note={cagrExplanation(metrics)} />
        <Metric label="Volatility" value={formatPercent(metrics.volatility)} note={volatilityExplanation(metrics, returnCount)} />
        <Metric label="Sharpe" value={formatRatio(metrics.sharpeRatio)} note={sharpeExplanation(metrics, returnCount)} />
        <Metric label="Max drawdown" value={formatPercent(metrics.maxDrawdown)} tone={metrics.maxDrawdown > 0 ? 'danger' : undefined} />
      </dl>

      <p className="mt-3 text-xs text-ink-muted">Drawdown series is not currently exposed by the engine — maximum drawdown is shown as a single metric.</p>

      <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-4 border-t border-border pt-6 sm:grid-cols-3 lg:grid-cols-6">
        <Metric label="Closed trades" value={String(metrics.closedTradeCount)} compact />
        <Metric label="Win rate" value={formatPercent(metrics.winRate)} compact note={winRateExplanation(metrics)} />
        <Metric label="Average win" value={formatStatMoney(metrics.averageWin)} compact note={averageWinExplanation(metrics)} />
        <Metric label="Average loss" value={formatStatMoney(metrics.averageLoss)} compact note={averageLossExplanation(metrics)} />
        <Metric label="Total commission" value={formatMoney(totalCommission)} compact />
        <Metric label="Total slippage" value={formatMoney(totalSlippageCost)} compact />
      </dl>
    </div>
  );
}

function Metric({ label, value, tone, compact, note }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className={`mt-1 tabular-nums font-semibold text-ink ${compact ? 'text-base' : 'text-2xl'} ${tone ? TONE_TEXT[tone] : ''}`}>
        {value}
      </dd>
      {note ? <p className="mt-0.5 text-xs text-ink-muted">{note}</p> : null}
    </div>
  );
}
