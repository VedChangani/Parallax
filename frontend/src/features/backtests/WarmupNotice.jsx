import { Badge } from '../../components/Badge.jsx';

export function WarmupNotice({ startDate, firstBarDate, firstEvaluableDate, inactiveBarCount, totalBarCount }) {
  if (firstBarDate === undefined || firstEvaluableDate === firstBarDate) {
    return null;
  }

  const hasCounts =
    typeof inactiveBarCount === 'number' && typeof totalBarCount === 'number' && totalBarCount > 0;

  if (firstEvaluableDate === null || firstEvaluableDate === undefined) {
    return (
      <div role="status" className="flex flex-wrap items-start gap-3 rounded-md border border-border bg-page/60 p-4 text-sm">
        <Badge tone="neutral">Never evaluated</Badge>
        <p className="text-ink-secondary">
          This strategy&rsquo;s indicators never had enough history to produce a value during the requested range, so
          no entry or exit signal was possible at any point in this run.
          {hasCounts ? ` No signal could be generated on any of the ${totalBarCount} bars in range.` : null}
        </p>
      </div>
    );
  }

  return (
    <div role="status" className="flex flex-wrap items-start gap-3 rounded-md border border-border bg-page/60 p-4 text-sm">
      <Badge tone="neutral">Warm-up period</Badge>
      <p className="text-ink-secondary">
        Indicators needed history to warm up: the strategy could not be evaluated until{' '}
        <span className="font-medium text-ink">{firstEvaluableDate}</span>, later than the requested start date of{' '}
        <span className="font-medium text-ink">{startDate}</span>.
        {hasCounts
          ? ` No signal could be generated on ${inactiveBarCount} of ${totalBarCount} bars in range.`
          : null}
      </p>
    </div>
  );
}
