import { useEffect, useMemo, useRef } from 'react';
import uPlot from 'uplot';
import 'uplot/dist/uPlot.min.css';
import { drawdownChartData } from './drawdownData.js';

const HEIGHT = 160;
const DRAWDOWN_COLOR = '#b91c1c';
const DRAWDOWN_FILL = 'rgba(185, 28, 28, 0.14)';

export function DrawdownChart({ points }) {
  const containerRef = useRef(null);
  const data = useMemo(() => drawdownChartData(points), [points]);
  const plottable = data.ys.length >= 2;

  useEffect(() => {
    if (!containerRef.current || !plottable) return undefined;

    const deepest = data.ys[data.deepestIndex];
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
