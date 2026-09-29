import { Link } from 'react-router';
import { CopyButton } from '../../components/CopyButton.jsx';
import { fractionTextToPercentText } from '../../lib/decimal.js';
import { formatDate, formatMoney } from '../../lib/format.js';

export function ResearchInputsPanel({ run, marketName, marketSymbol, strategyName }) {
  const slippagePercent = fractionTextToPercentText(run.slippageRate);

  return (
    <div className="rounded-md border border-border bg-surface p-5">
      <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Research inputs</h2>

      <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-4">
        <div>
          <dt className="text-xs text-ink-muted">Market</dt>
          <dd className="font-semibold text-ink">
            <Link to={`/datasets/${run.datasetId}`} className="hover:text-accent hover:underline">
              {marketName ?? marketSymbol ?? `Market #${run.datasetId}`}
            </Link>
          </dd>
          <dd className="text-ink-secondary">Snapshot v{run.datasetVersion}</dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">Strategy</dt>
          <dd className="font-semibold text-ink">
            <Link to={`/strategies/${run.strategyId}`} className="hover:text-accent hover:underline">
              {strategyName ?? `Strategy #${run.strategyId}`}
            </Link>
          </dd>
          <dd className="text-ink-secondary">Version v{run.strategyVersion}</dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">Period</dt>
          <dd className="tabular-nums font-semibold text-ink">
            {run.startDate} → {run.endDate}
          </dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">First evaluable date</dt>
          <dd className="tabular-nums font-semibold text-ink">{formatDate(run.firstEvaluableDate)}</dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">Engine semantics</dt>
          <dd className="font-semibold text-ink">v{run.engineSemanticsVersion}</dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">Initial capital</dt>
          <dd className="tabular-nums font-semibold text-ink">{formatMoney(run.initialCapital)}</dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">Commission per fill</dt>
          <dd className="tabular-nums font-semibold text-ink">{formatMoney(run.commissionPerFill)}</dd>
        </div>

        <div>
          <dt className="text-xs text-ink-muted">Slippage</dt>
          <dd className="tabular-nums font-semibold text-ink">{slippagePercent}%</dd>
        </div>
      </dl>

      <div className="mt-4 space-y-2 border-t border-border pt-4">
        <HashRow label="Strategy definition" value={run.definitionHash} />
        <HashRow label="Dataset snapshot" value={run.contentHash} />
      </div>
    </div>
  );
}

function HashRow({ label, value }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="text-xs font-medium uppercase tracking-wide text-ink-muted">{label}</span>
      <code className="truncate font-mono text-xs text-ink-secondary">{value}</code>
      <CopyButton value={value} />
    </div>
  );
}
