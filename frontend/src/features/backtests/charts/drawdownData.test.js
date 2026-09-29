import { describe, expect, it } from 'vitest';
import { drawdownChartData } from './drawdownData.js';

const point = (date, drawdown) => ({ date, equity: '100', benchmarkEquity: '100', drawdown });

describe('drawdownChartData', () => {
  it('keeps the equity points\' dates exactly, in order', () => {
    const { dates, xs } = drawdownChartData([point('2024-01-02', 0), point('2024-01-03', 0.05), point('2024-01-08', 0.02)]);

    expect(dates).toEqual(['2024-01-02', '2024-01-03', '2024-01-08']);
    expect(xs).toEqual([
      Date.parse('2024-01-02T00:00:00Z') / 1000,
      Date.parse('2024-01-03T00:00:00Z') / 1000,
      Date.parse('2024-01-08T00:00:00Z') / 1000,
    ]);
  });

  it('plots drawdown as negative percentage points, with zero meaning no drawdown', () => {
    const { ys } = drawdownChartData([point('2024-01-02', 0), point('2024-01-03', 0.25), point('2024-01-04', 0.081)]);

    expect(ys[0]).toBe(0);
    expect(ys[1]).toBeCloseTo(-25, 10);
    expect(ys[2]).toBeCloseTo(-8.1, 10);
  });

  it('never produces negative zero for a point at a peak', () => {
    const { ys } = drawdownChartData([point('2024-01-02', 0), point('2024-01-03', 0)]);

    expect(Object.is(ys[0], 0)).toBe(true);
    expect(Object.is(ys[1], 0)).toBe(true);
  });

  it('reports the deepest drawdown, first occurrence on ties', () => {
    const { deepestIndex } = drawdownChartData([point('a', 0), point('b', 0.1), point('c', 0.3), point('d', 0.3), point('e', 0)]);

    expect(deepestIndex).toBe(2);
  });

  it('skips points without a finite numeric drawdown rather than plotting a bogus zero', () => {
    const { dates, ys } = drawdownChartData([
      point('2024-01-02', 0.1),
      { date: '2024-01-03', equity: '100', benchmarkEquity: '100' },
      point('2024-01-04', Number.NaN),
      point('2024-01-05', '0.2'),
      point('2024-01-08', 0.2),
    ]);

    expect(dates).toEqual(['2024-01-02', '2024-01-08']);
    expect(ys).toHaveLength(2);
  });

  it('handles an empty series', () => {
    expect(drawdownChartData([])).toEqual({ dates: [], xs: [], ys: [], deepestIndex: -1 });
  });

  it('handles a series that never draws down', () => {
    const { ys, deepestIndex } = drawdownChartData([point('a', 0), point('b', 0), point('c', 0)]);

    expect(ys).toEqual([0, 0, 0]);
    expect(deepestIndex).toBe(0);
  });
});
