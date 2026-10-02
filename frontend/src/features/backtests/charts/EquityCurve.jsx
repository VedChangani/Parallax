import { useEffect, useRef } from 'react';
import uPlot from 'uplot';
import 'uplot/dist/uPlot.min.css';

const HEIGHT = 320;
const STRATEGY_COLOR = '#1d4ed8';
const BENCHMARK_COLOR = '#868c9c';

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
