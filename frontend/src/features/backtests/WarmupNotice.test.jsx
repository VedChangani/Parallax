import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { WarmupNotice } from './WarmupNotice.jsx';

describe('WarmupNotice', () => {
  it('shows no notice when firstEvaluableDate equals the run\'s own first bar (CASE 1)', () => {
    render(<WarmupNotice startDate="2024-01-01" firstBarDate="2024-01-01" firstEvaluableDate="2024-01-01" />);
    expect(screen.queryByRole('status')).toBeNull();
  });

  // M-1 regression: a weekend/holiday startDate has no bar of its own, so the
  // run's genuine first bar is the next trading day. Readiness on that very
  // first real bar must NOT be reported as a delayed warm-up (and must never
  // produce a nonsensical "0 of N bars" count) merely because it differs from
  // the raw requested startDate string.
  it('shows no false warm-up notice for a weekend start followed by the next trading day (M-1)', () => {
    // 2024-01-06 is a Saturday; the market's first real bar is Monday 2024-01-08.
    render(
      <WarmupNotice
        startDate="2024-01-06"
        firstBarDate="2024-01-08"
        firstEvaluableDate="2024-01-08"
        inactiveBarCount={0}
        totalBarCount={5}
      />,
    );

    expect(screen.queryByText(/Warm-up period/)).toBeNull();
    expect(screen.queryByText(/Never evaluated/)).toBeNull();
    expect(screen.queryByText(/0 of/)).toBeNull();
  });

  it('shows the warm-up message when indicators became ready after the run\'s own first bar (CASE 2)', () => {
    render(
      <WarmupNotice
        startDate="2024-01-08"
        firstBarDate="2024-01-08"
        firstEvaluableDate="2024-02-05"
        inactiveBarCount={20}
        totalBarCount={40}
      />,
    );

    expect(screen.getByText('Warm-up period')).toBeTruthy();
    expect(screen.getByText(/could not be evaluated until/)).toBeTruthy();
    expect(screen.getByText('2024-02-05')).toBeTruthy();
    expect(screen.getByText('2024-01-08')).toBeTruthy();
    expect(screen.getByRole('status').textContent).toMatch(/No signal could be generated on 20 of 40 bars in range/);
  });

  it('still shows the warm-up message for a weekend start once indicators genuinely need more bars than the first one', () => {
    // The first real bar (Monday 2024-01-08) is not itself the ready date -
    // readiness is genuinely later, so this must still warn, just correctly
    // measured against the real first bar rather than the weekend startDate.
    render(
      <WarmupNotice
        startDate="2024-01-06"
        firstBarDate="2024-01-08"
        firstEvaluableDate="2024-02-05"
        inactiveBarCount={20}
        totalBarCount={40}
      />,
    );

    expect(screen.getByText('Warm-up period')).toBeTruthy();
    // The message still cites the originally requested startDate for context.
    expect(screen.getByText('2024-01-06')).toBeTruthy();
  });

  it('explains a null firstEvaluableDate as "never evaluated" (CASE 3)', () => {
    render(<WarmupNotice startDate="2024-01-08" firstBarDate="2024-01-08" firstEvaluableDate={null} totalBarCount={10} />);

    expect(screen.getByText('Never evaluated')).toBeTruthy();
    expect(screen.getByText(/never had enough history/)).toBeTruthy();
  });

  it('renders nothing while the run\'s first bar is not yet known, rather than risk a false positive', () => {
    render(<WarmupNotice startDate="2024-01-06" firstBarDate={undefined} firstEvaluableDate="2024-01-08" />);
    expect(screen.queryByRole('status')).toBeNull();
  });
});
