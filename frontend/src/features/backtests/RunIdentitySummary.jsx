export function RunIdentitySummary({ market, snapshotVersion, snapshotDetail, strategy, strategyVersion, strategyVersionDetail }) {
  return (
    <div className="rounded-md border border-border bg-surface p-5 shadow-[2px_2px_0_0_var(--color-border)]">
      <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-muted">Research inputs</h2>

      <dl className="mt-4 space-y-4 text-sm">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-ink-muted">Market</dt>
          <dd className="mt-0.5 font-semibold text-ink">{market ? `${market.name} (${market.symbol})` : '—'}</dd>
          <dd className="text-ink-secondary">{snapshotVersion !== undefined ? `Snapshot v${snapshotVersion}` : 'No snapshot selected'}</dd>
        </div>

        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-ink-muted">Strategy</dt>
          <dd className="mt-0.5 font-semibold text-ink">{strategy ? strategy.name : '—'}</dd>
          <dd className="text-ink-secondary">{strategyVersion !== undefined ? `Version v${strategyVersion}` : 'No version selected'}</dd>
        </div>
      </dl>

      {snapshotDetail || strategyVersionDetail ? (
        <details className="mt-4 border-t border-border pt-3 text-xs text-ink-muted">
          <summary className="cursor-pointer select-none font-medium">Technical detail</summary>
          <div className="mt-2 space-y-1 break-all font-mono">
            {snapshotDetail ? <p>Dataset hash: {snapshotDetail.contentHash}</p> : null}
            {strategyVersionDetail ? <p>Definition hash: {strategyVersionDetail.definitionHash}</p> : null}
          </div>
        </details>
      ) : null}
    </div>
  );
}
