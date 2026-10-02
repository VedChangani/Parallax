import { formatMoney, formatPercent, formatRatio, formatSignedPercent, formatStatMoney } from '../../lib/format.js';
import {
  averageLossExplanation,
  averageWinExplanation,
  cagrExplanation,
  profitFactorExplanation,
  sharpeExplanation,
  volatilityExplanation,
  winRateExplanation,
} from './missingMetricExplanations.js';

const TONE_TEXT = { success: 'text-success', danger: 'text-danger' };

function returnTone(value) {
  if (value === null || value === undefined) return undefined;
  if (value > 0) return 'success';
  if (value < 0) return 'danger';
  return undefined;
}

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

      <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-4 border-t border-border pt-6 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7">
        <Metric label="Closed trades" value={String(metrics.closedTradeCount)} compact />
        <Metric label="Win rate" value={formatPercent(metrics.winRate)} compact note={winRateExplanation(metrics)} />
        <Metric label="Average win" value={formatStatMoney(metrics.averageWin)} compact note={averageWinExplanation(metrics)} />
        <Metric label="Average loss" value={formatStatMoney(metrics.averageLoss)} compact note={averageLossExplanation(metrics)} />
        <Metric label="Profit factor" value={formatRatio(metrics.profitFactor)} compact note={profitFactorExplanation(metrics)} />
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
