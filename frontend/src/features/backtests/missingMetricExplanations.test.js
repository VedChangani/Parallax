import { describe, expect, it } from 'vitest';
import {
  averageLossExplanation,
  averageWinExplanation,
  cagrExplanation,
  profitFactorExplanation,
  sharpeExplanation,
  volatilityExplanation,
  winRateExplanation,
} from './missingMetricExplanations.js';

const BASE_METRICS = {
  totalReturn: 0.1,
  cagr: 0.05,
  volatility: 0.2,
  sharpeRatio: 1.1,
  maxDrawdown: 0.05,
  closedTradeCount: 2,
  winRate: 0.5,
  averageWin: 100,
  averageLoss: -50,
};

describe('cagrExplanation', () => {
  it('returns undefined when CAGR is present', () => {
    expect(cagrExplanation(BASE_METRICS)).toBeUndefined();
  });

  it('explains a null CAGR as a sub-year span - its one and only emptiness condition', () => {
    expect(cagrExplanation({ ...BASE_METRICS, cagr: null })).toMatch(/less than 365 days/);
  });
});

describe('volatilityExplanation', () => {
  it('returns undefined when volatility is present', () => {
    expect(volatilityExplanation(BASE_METRICS, 10)).toBeUndefined();
  });

  it('explains a null volatility with the exact return count when known', () => {
    expect(volatilityExplanation({ ...BASE_METRICS, volatility: null }, 1)).toMatch(/Fewer than two.*\(1\)/);
  });

  it('explains a null volatility generically when the return count is not known', () => {
    expect(volatilityExplanation({ ...BASE_METRICS, volatility: null }, undefined)).toMatch(/Fewer than two/);
  });
});

describe('sharpeExplanation', () => {
  it('returns undefined when Sharpe is present', () => {
    expect(sharpeExplanation(BASE_METRICS, 10)).toBeUndefined();
  });

  it('attributes a null Sharpe to too few returns when the return count confirms it', () => {
    expect(sharpeExplanation({ ...BASE_METRICS, sharpeRatio: null, volatility: null }, 1)).toMatch(/Fewer than two.*\(1\)/);
  });

  it('attributes a null Sharpe to zero dispersion when volatility is present and exactly zero', () => {
    expect(sharpeExplanation({ ...BASE_METRICS, sharpeRatio: null, volatility: 0 }, 10)).toMatch(/identical/);
  });

  it('falls back to a combined explanation when the return count is not known', () => {
    expect(sharpeExplanation({ ...BASE_METRICS, sharpeRatio: null, volatility: null }, undefined)).toMatch(
      /Fewer than two return observations were recorded, or every return was identical/,
    );
  });
});

describe('winRateExplanation', () => {
  it('returns undefined when win rate is present', () => {
    expect(winRateExplanation(BASE_METRICS)).toBeUndefined();
  });

  it('explains a null win rate as no closed trades - its one and only emptiness condition', () => {
    expect(winRateExplanation({ ...BASE_METRICS, winRate: null, closedTradeCount: 0 })).toMatch(/No trades were closed/);
  });
});

describe('averageWinExplanation', () => {
  it('returns undefined when average win is present', () => {
    expect(averageWinExplanation(BASE_METRICS)).toBeUndefined();
  });

  it('explains a null average win as no closed trades when there were none at all', () => {
    expect(averageWinExplanation({ ...BASE_METRICS, averageWin: null, closedTradeCount: 0 })).toMatch(/No trades were closed/);
  });

  it('explains a null average win as no winning trades when some trades did close', () => {
    expect(averageWinExplanation({ ...BASE_METRICS, averageWin: null, closedTradeCount: 3 })).toMatch(/No winning trades/);
  });
});

describe('averageLossExplanation', () => {
  it('returns undefined when average loss is present', () => {
    expect(averageLossExplanation(BASE_METRICS)).toBeUndefined();
  });

  it('explains a null average loss as no closed trades when there were none at all', () => {
    expect(averageLossExplanation({ ...BASE_METRICS, averageLoss: null, closedTradeCount: 0 })).toMatch(/No trades were closed/);
  });

  it('explains a null average loss as no losing trades when some trades did close', () => {
    expect(averageLossExplanation({ ...BASE_METRICS, averageLoss: null, closedTradeCount: 3 })).toMatch(/No losing trades/);
  });
});

describe('profitFactorExplanation', () => {
  it('returns undefined when profit factor is present, including an exact zero', () => {
    expect(profitFactorExplanation({ ...BASE_METRICS, profitFactor: 1.5 })).toBeUndefined();
    expect(profitFactorExplanation({ ...BASE_METRICS, profitFactor: 0 })).toBeUndefined();
  });

  it('explains a null profit factor as needing a losing closed trade', () => {
    expect(profitFactorExplanation({ ...BASE_METRICS, profitFactor: null })).toMatch(/losing closed trade/);
  });
});
