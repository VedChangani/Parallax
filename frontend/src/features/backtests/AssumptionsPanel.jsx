import { Badge } from '../../components/Badge.jsx';
import { ADJUSTMENT_BASIS_LABELS, SOURCE_LABELS } from '../../lib/datasetLabels.js';

export function AssumptionsPanel({ snapshot }) {
  const isRaw = snapshot?.adjustmentBasis === 'RAW';

  return (
    <div className="rounded-md border border-border bg-surface p-5 text-sm">
      <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Assumptions</h2>

      {snapshot?.source ? (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Badge tone="neutral">{SOURCE_LABELS[snapshot.source] ?? snapshot.source}</Badge>
          <Badge tone={isRaw ? 'warning' : 'neutral'}>
            {ADJUSTMENT_BASIS_LABELS[snapshot.adjustmentBasis] ?? snapshot.adjustmentBasis}
          </Badge>
          {isRaw ? <span className="text-ink-secondary">Raw prices are not split-adjusted.</span> : null}
        </div>
      ) : null}

      <ul className="mt-4 list-disc space-y-1.5 pl-5 text-ink-secondary">
        <li>A signal is generated at a bar&rsquo;s close; the resulting market order executes at the next bar&rsquo;s open, adjusted by the configured slippage.</li>
        <li>Position sizing reserves one commission in cash at entry for the eventual exit, so an affordable buy is never left unable to sell.</li>
        <li>A position still open at the end of the run is marked at the final bar&rsquo;s close - it is never force-liquidated, and no hypothetical exit commission or slippage is charged.</li>
        <li>Buy &amp; hold represents the same starting capital invested once, at the first in-range bar&rsquo;s open, and held for the entire period - never traded, rebalanced, or sold.</li>
        {isRaw ? (
          <li>Prices are RAW (unadjusted) - dividends are never added back into either the strategy&rsquo;s or the benchmark&rsquo;s return.</li>
        ) : null}
      </ul>

      <p className="mt-4 border-t border-border pt-3 font-medium text-ink-secondary">
        Historical simulation — not indicative of future results.
      </p>
    </div>
  );
}
