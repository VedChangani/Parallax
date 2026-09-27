import { formatMoney } from '../../lib/format.js';

/**
 * A compact "date · close" signal snapshot with its indicator values tucked
 * behind a native disclosure - shared by the Trades and Rejections views
 * (D-34 Batch 5 §12/§14). Indicator values are rendered exactly as the
 * backend returns them (`Double.toString`), never rounded or reformatted.
 *
 * @param {object} props
 * @param {string} props.date
 * @param {string} props.close
 * @param {import('../../api/types.js').BacktestIndicatorValueResponse[]} props.indicators
 */
export function IndicatorSnapshot({ date, close, indicators }) {
  return (
    <div className="text-xs">
      <p className="tabular-nums text-ink">
        {date} · {formatMoney(close)}
      </p>
      {indicators && indicators.length > 0 ? (
        <details>
          <summary className="cursor-pointer select-none text-ink-muted hover:text-ink">Indicators</summary>
          <ul className="mt-1 space-y-0.5 whitespace-nowrap font-mono text-ink-secondary">
            {indicators.map((indicator) => (
              <li key={`${indicator.type}-${indicator.period}`}>
                {indicator.type}({indicator.period}) = {indicator.value}
              </li>
            ))}
          </ul>
        </details>
      ) : null}
    </div>
  );
}
