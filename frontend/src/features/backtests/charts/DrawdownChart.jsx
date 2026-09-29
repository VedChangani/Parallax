import { useEffect, useMemo, useRef } from 'react';
import uPlot from 'uplot';
import 'uplot/dist/uPlot.min.css';
import { drawdownChartData } from './drawdownData.js';

const HEIGHT = 160;
const DRAWDOWN_COLOR = '#b91c1c';
const DRAWDOWN_FILL = 'rgba(185, 28, 28, 0.14)';

/**
 * A compact drawdown chart for the run's own persisted equity curve: the
 * per-point `drawdown` from `GET /api/backtest-runs/{id}/equity-curve`
 * (D-41), plotted as a negative percentage so 0% is "at a peak" and lower
 * is deeper. The percentage/negation conversion happens only in
 * {@link drawdownChartData}, purely for the canvas; the accessible summary
 * below states the original values.
 *
 * The y-range always includes 0 (a run that never drew down still gets a
 * flat line at 0%), and uPlot itself decimates to the pixel width, so long
 * runs stay readable.
 *
 * @param {object} props
 * @param {import('../../../api/types.js').BacktestEquityPointResponse[]} props.points
 */
export function DrawdownChart({ points }) {
  const containerRef = useRef(null);
  const data = useMemo(() => drawdownChartData(points), [points]);
  const plottable = data.ys.length >= 2;

  useEffect(() => {
    if (!containerRef.current || !plottable) return undefined;

    const deepest = data.ys[data.deepestIndex];
    // Always show 0 at the top with a little headroom below the deepest point.
    const yMin = deepest < 0 ? deepest * 1.1 : -1;

    const plot = new uPlot(
      {
        width: containerRef.current.clientWidth,
        height: HEIGHT,
        scales: { x: { time: true }, y: { range: () => [yMin, 0] } },
        cursor: { drag: { x: false, y: false } },
        legend: { live: true },
        series: [
          {},
          {
            label: 'Drawdown',
            stroke: DRAWDOWN_COLOR,
            fill: DRAWDOWN_FILL,
            width: 1.5,
            points: { show: false },
            value: (_chart, value) => (value === null || value === undefined ? '--' : `${value.toFixed(2)}%`),
          },
        ],
        axes: [
          {},
          {
            size: 72,
            values: (_chart, values) => values.map((value) => `${value.toFixed(0)}%`),
          },
        ],
      },
      [data.xs, data.ys],
      containerRef.current,
    );

    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width;
      if (width && width > 0) plot.setSize({ width, height: HEIGHT });
    });
    observer.observe(containerRef.current);

    return () => {
      observer.disconnect();
      plot.destroy();
    };
  }, [data, plottable]);

  if (data.ys.length === 0) {
    return <p className="text-sm text-ink-secondary">No drawdown data was returned for this run.</p>;
  }
  if (!plottable) {
    return <p className="text-sm text-ink-secondary">Too few equity points to chart a drawdown.</p>;
  }

  const first = data.dates[0];
  const last = data.dates[data.dates.length - 1];
  const deepestDrawdown = -data.ys[data.deepestIndex];

  return (
    <div>
      <div ref={containerRef} className="parallax-drawdown-chart" />
      <p className="sr-only">
        Drawdown from {first} to {last}. Deepest drawdown {deepestDrawdown.toFixed(2)}% on {data.dates[data.deepestIndex]}.
      </p>
    </div>
  );
}
