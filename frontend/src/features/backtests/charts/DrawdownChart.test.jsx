import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DrawdownChart } from './DrawdownChart.jsx';

function points(count, drawdownAt = () => 0) {
  return Array.from({ length: count }, (_, i) => ({
    date: new Date(Date.UTC(2020, 0, 1) + i * 86_400_000).toISOString().slice(0, 10),
    equity: '100000',
    benchmarkEquity: '100000',
    drawdown: drawdownAt(i),
  }));
}

describe('DrawdownChart', () => {
  it('summarises the exact date range and the deepest drawdown', () => {
    render(<DrawdownChart points={points(4, (i) => [0, 0.05, 0.2, 0.1][i])} />);

    const summary = screen.getByText(/Drawdown from/);
    expect(summary.textContent).toContain('2020-01-01 to 2020-01-04');
    expect(summary.textContent).toContain('Deepest drawdown 20.00% on 2020-01-03');
  });

  it('plots a run that never drew down (all zero) without error', () => {
    render(<DrawdownChart points={points(5)} />);

    expect(screen.getByText(/Deepest drawdown 0.00% on 2020-01-01/)).toBeTruthy();
  });

  it('handles a long, MSFT-scale series (10,000 daily points) without error', () => {
    const long = points(10_000, (i) => Math.abs(Math.sin(i / 50)) * 0.3);

    render(<DrawdownChart points={long} />);

    const summary = screen.getByText(/Drawdown from/);
    expect(summary.textContent).toContain('2020-01-01 to ');
    expect(summary.textContent).toMatch(/Deepest drawdown \d+\.\d{2}% on \d{4}-\d{2}-\d{2}/);
  });

  it('shows a message, not a chart, for an empty series', () => {
    render(<DrawdownChart points={[]} />);

    expect(screen.getByText('No drawdown data was returned for this run.')).toBeTruthy();
  });

  it('shows a message, not a chart, for a single point', () => {
    render(<DrawdownChart points={points(1)} />);

    expect(screen.getByText('Too few equity points to chart a drawdown.')).toBeTruthy();
  });

  it('ignores points that lack a numeric drawdown', () => {
    render(<DrawdownChart points={[...points(3, (i) => [0, 0.1, 0][i]), { date: '2020-02-01', equity: '1', benchmarkEquity: '1' }]} />);

    expect(screen.getByText(/Drawdown from 2020-01-01 to 2020-01-03/)).toBeTruthy();
  });
});
