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
 * Renders nothing for the ordinary case — `firstEvaluableDate` equals the
 * date of the run's own <em>first actual bar</em> (`firstBarDate`, CASE 1,
 * no special warning needed). This is deliberately compared against the
 * first real bar the run actually has, never the raw requested `startDate`
 * string (M-1, Phase 10 Batch 4): a `startDate` that falls on a weekend or
 * holiday has no bar of its own, so the run's genuine first bar is later
 * than it, and comparing against `startDate` directly produced a false
 * "warm-up" notice — including a nonsensical "0 of N bars" count — for a
 * strategy that was in fact ready immediately, on its very first available
 * bar. For the other two cases it renders a single, neutral-toned notice -
 * visually noticeable, never alarming, and never phrased as the strategy
 * "losing" or "underperforming":
 *
 * - CASE 2 (`firstEvaluableDate` is later than `firstBarDate`): the
 *   indicators were still warming up: no signal was possible before that
 *   date. The message still cites the originally requested `startDate` for
 *   context ("later than the requested start date of ...") - that remains
 *   an accurate, useful fact regardless of which bar was the first real one.
 * - CASE 3 (`firstEvaluableDate` is `null`): the indicators never became
 *   ready during the requested range, so no signal was ever possible.
 *
 * `inactiveBarCount`/`totalBarCount` are optional and, when supplied, must
 * already be derived from data the page legitimately fetched for another
 * reason (the equity curve, which spans the full requested range) - this
 * component performs no fetch and introduces no new backend arithmetic.
 *
 * @param {object} props
 * @param {string} props.startDate - the originally requested ISO-8601 start
 *   date, shown in CASE 2's message text only
 * @param {string | undefined} props.firstBarDate - the ISO-8601 date of the
 *   run's own first equity-curve point (its genuine first bar), used for the
 *   CASE 1 comparison; `undefined` while the equity curve is still loading
 *   suppresses the notice entirely rather than risk a false positive
 * @param {string | null} props.firstEvaluableDate
 * @param {number} [props.inactiveBarCount]
 * @param {number} [props.totalBarCount]
 */
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
