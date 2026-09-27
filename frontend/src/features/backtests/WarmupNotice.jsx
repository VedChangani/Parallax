import { Badge } from '../../components/Badge.jsx';

/**
 * Explains `firstEvaluableDate` to the user (C1, Phase 9 Batch 1): the exact
 * bar on which every indicator the strategy needs first had enough history
 * to produce a value, so the strategy could actually be evaluated for entry
 * or exit. The engine already computes and persists this (D-25/D-34); this
 * component only presents it - it never changes Backtester chronology, the
 * benchmark (which is unaffected by warm-up: it enters at the first in-range
 * bar regardless, D-28), or any stored value.
 *
 * Renders nothing for the ordinary case (`firstEvaluableDate === startDate`
 * - CASE 1, no special warning needed). For the other two cases it renders a
 * single, neutral-toned notice - visually noticeable, never alarming, and
 * never phrased as the strategy "losing" or "underperforming":
 *
 * - CASE 2 (`firstEvaluableDate` is later than `startDate`): the indicators
 *   were still warming up: no signal was possible before that date.
 * - CASE 3 (`firstEvaluableDate` is `null`): the indicators never became
 *   ready during the requested range, so no signal was ever possible.
 *
 * `inactiveBarCount`/`totalBarCount` are optional and, when supplied, must
 * already be derived from data the page legitimately fetched for another
 * reason (the equity curve, which spans the full requested range) - this
 * component performs no fetch and introduces no new backend arithmetic.
 *
 * @param {object} props
 * @param {string} props.startDate - ISO-8601 date
 * @param {string | null} props.firstEvaluableDate
 * @param {number} [props.inactiveBarCount]
 * @param {number} [props.totalBarCount]
 */
export function WarmupNotice({ startDate, firstEvaluableDate, inactiveBarCount, totalBarCount }) {
  if (firstEvaluableDate === startDate) {
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
