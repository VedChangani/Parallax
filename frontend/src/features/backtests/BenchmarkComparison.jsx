import { formatSignedPercent } from '../../lib/format.js';

/**
 * Strategy vs. buy-and-hold total return (D-34 Batch 5 §8). `excessReturn`
 * is presentation-only arithmetic on the two already-persisted total-return
 * values - never a new backend metric, never derived from raw fills/equity.
 *
 * @param {object} props
 * @param {number} props.strategyReturn
 * @param {number} props.benchmarkReturn
 */
export function BenchmarkComparison({ strategyReturn, benchmarkReturn }) {
  const excessReturn = strategyReturn - benchmarkReturn;

  return (
    <dl className="grid grid-cols-3 divide-x divide-border rounded-md border border-border bg-page/60">
      <Stat label="Strategy" value={strategyReturn} />
      <Stat label="Buy & hold" value={benchmarkReturn} />
      <Stat label="Excess return" value={excessReturn} emphasize />
    </dl>
  );
}

function Stat({ label, value, emphasize }) {
  const tone = value > 0 ? 'text-success' : value < 0 ? 'text-danger' : 'text-ink';
  return (
    <div className="p-4 text-center">
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</dt>
      <dd className={`mt-1 tabular-nums font-semibold ${emphasize ? 'text-xl' : 'text-lg'} ${tone}`}>{formatSignedPercent(value)}</dd>
    </div>
  );
}
