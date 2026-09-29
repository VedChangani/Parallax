import { formatMoney } from '../../lib/format.js';

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
