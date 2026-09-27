import { useEffect, useRef } from 'react';
import uPlot from 'uplot';
import 'uplot/dist/uPlot.min.css';

const HEIGHT = 320;
const STRATEGY_COLOR = '#1d4ed8';
const BENCHMARK_COLOR = '#868c9c';

/**
 * Plots the run's own persisted equity curve (D-34 Batch 5 §10): strategy
 * equity vs. the buy-and-hold benchmark equity, taken directly from
 * `GET /api/backtest-runs/{id}/equity-curve` - never recomputed from
 * cash/quantity/marketValue. Converting each point's exact decimal string
 * to a number happens ONLY here, purely so uPlot's canvas renderer has a
 * numeric coordinate to plot; nothing else in the app ever sees that
 * number, and the accessible summary below states the original strings.
 *
 * @param {object} props
 * @param {import('../../../api/types.js').BacktestEquityPointResponse[]} props.points
 */
export function EquityCurve({ points }) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current || points.length === 0) return undefined;

    const xs = points.map((point) => Date.parse(`${point.date}T00:00:00Z`) / 1000);
    const strategySeries = points.map((point) => Number(point.equity));
    const benchmarkSeries = points.map((point) => Number(point.benchmarkEquity));

    const plot = new uPlot(
      {
        width: containerRef.current.clientWidth,
        height: HEIGHT,
        scales: { x: { time: true } },
        // No drag-to-zoom, no animation - a static, read-only historical chart.
        cursor: { drag: { x: false, y: false } },
        legend: { live: true },
        series: [
          {},
          { label: 'Strategy equity', stroke: STRATEGY_COLOR, width: 2, points: { show: false } },
          { label: 'Buy & hold equity', stroke: BENCHMARK_COLOR, width: 2, dash: [6, 4], points: { show: false } },
        ],
        axes: [
          {},
          {
            size: 72,
            values: (_chart, values) => values.map((value) => value.toLocaleString('en-US', { maximumFractionDigits: 0 })),
          },
        ],
      },
      [xs, strategySeries, benchmarkSeries],
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
  }, [points]);

  if (points.length === 0) return null;

  const first = points[0];
  const last = points[points.length - 1];

  return (
    <div>
      <div ref={containerRef} className="parallax-equity-chart" />
      <p className="sr-only">
        Equity curve from {first.date} to {last.date}. Strategy equity moved from {first.equity} to {last.equity}. Buy-and-hold
        equity moved from {first.benchmarkEquity} to {last.benchmarkEquity}.
      </p>
    </div>
  );
}
