# Parallax Project Progress

## Current Milestone

`RelativeStrengthIndex` (RSI, Wilder smoothing) implemented in
`engine.indicator`, with hand-calculated tests. All three V1 indicators
(SMA, EMA, RSI) are now implemented.

## Completed

### Bootstrap (repository/module architecture)

- Root Maven aggregator `pom.xml` (`packaging=pom`, modules `engine`,
  `backend`).
- `engine/` plain Java module (JUnit 5 only), `package-info.java`
  placeholder and a smoke test.
- `backend/` builds as a module of the root aggregator with its generated
  Spring Boot configuration untouched.
- Maven wrapper moved from `backend/` to the repository root.
- Frontend (React + Vite) verified to build unmodified.

### Engine architecture checkpoint

- Documented the approved V1 engine design in `docs/architecture.md`
  ("Engine Architecture"): domain model, rejected abstractions, exact
  chronological execution contract, inclusive date range with separate
  lookback, range boundaries, look-ahead protection, state ownership,
  fill/signal/trade relationship, indicator semantics, execution semantics,
  portfolio accounting and identity, numerical policy, determinism and
  reproducibility, testing strategy, engine boundary.
- Recorded the frozen decisions as D-4 to D-16 in `docs/decisions.md`.

### Engine implementation batch 1: `Bar` and `BarSeries`

- `in.vedchangani.parallax.engine.data.Bar`: immutable record (`LocalDate
  date`, `BigDecimal open, high, low, close`, `long volume`). Validates at
  construction: date non-null; all prices > 0; `low <= min(open, close)`;
  `high >= max(open, close)`; `volume >= 0`. No symbol, provider, ID, or
  persistence annotation; matches D-4.
- `in.vedchangani.parallax.engine.data.BarSeries`: immutable value type
  (`String symbol`, `List<Bar> bars`). Validates symbol non-null/non-blank,
  bars non-null/non-empty, and strictly ascending dates (rejects duplicate
  or out-of-order dates rather than sorting or deduplicating). Takes a
  defensive copy via `List.copyOf` and exposes an unmodifiable list.
  `BarSeries` has no knowledge of `BacktestConfig`, date ranges,
  strategies, indicators, execution, or persistence — those are engine
  run-loop and backend concerns per the approved architecture.
- 22 new tests: `BarTest` (13 cases — valid bar, zero-volume flat bar, null
  date, zero/negative price on each OHLC field, low above open/close, high
  below open/close, negative volume) and `BarSeriesTest` (9 cases — valid
  ascending series, null/blank symbol, null/empty bar list, duplicate date,
  out-of-order date, unmodifiable returned list, defensive copy of a
  mutable input list).

### Namespace consistency correction

- The engine's approved root package is `in.vedchangani.parallax.engine`.
  `Bar`/`BarSeries`/`BarTest`/`BarSeriesTest` were moved to this root in an
  earlier pass, but `package-info.java` and `EngineSmokeTest` had been left
  under the old root `in.vedchangani.engine`. Both were moved and their
  package declarations updated to `in.vedchangani.parallax.engine`. No
  behavior, field, or validation rule was changed; nothing else referenced
  the old package, so no other files needed updating.

### Engine implementation batch 2: `IndicatorType`, `IndicatorSpec`, `Indicator`

- `in.vedchangani.parallax.engine.indicator.IndicatorType`: enum with
  exactly `SMA`, `EMA`, `RSI`.
- `in.vedchangani.parallax.engine.indicator.IndicatorSpec`: immutable
  record (`IndicatorType type`, `int period`). Validates type non-null,
  `period >= 1`, and `period >= 2` for `RSI`. Carries no runtime state, no
  calculated value, no symbol, timeframe, date range, or persistence
  concern — it is the definition of what is calculated, not the
  calculation. Records compare by value, so two specs with the same type
  and period are equal.
- `in.vedchangani.parallax.engine.indicator.Indicator`: the runtime
  interface — `void update(BigDecimal close)`, `boolean isReady()`,
  `double value()`. Deliberately minimal: no `calculate(BarSeries)`, no
  `update(List)`, no history accessor, no listeners, no `reset()` (no
  concrete V1 need demonstrated). One instance is created per backtest run
  from an `IndicatorSpec`; instances are never shared across runs.
- Not-ready contract: calling `value()` before `isReady()` is `true` must
  throw `IllegalStateException`. No `IndicatorValue` wrapper, no
  `Optional`-based result — fail-fast on misuse rather than a sentinel like
  `0` or `NaN`. Documented in the interface's Javadoc; no concrete
  implementation exists yet to unit test this, since SMA/EMA/RSI are the
  next batch.
- Factory: not created. No concrete `Indicator` implementation exists yet,
  so there is nothing for a factory to select between. Revisit once
  SMA/EMA/RSI exist and it's clear whether construction logic is shared.
- 12 new tests in `IndicatorSpecTest`: valid SMA/EMA/RSI specs, null type,
  zero period, negative period, RSI period 1 rejected, RSI period 2 and
  SMA period 1 accepted, equals/hashCode across equal and differing specs.
  The `Indicator` interface itself was not given a test double, since the
  only implementations that would exercise it (SMA/EMA/RSI) are a
  deliberately separate batch and a throwaway fake would test nothing real.

### Engine implementation batch 3: `SimpleMovingAverage`

- `in.vedchangani.parallax.engine.indicator.SimpleMovingAverage`: the first
  concrete `Indicator`. Maintains a fixed-size `double[period]` circular
  buffer plus a running sum — O(period) memory, O(1) per `update`. No
  `BarSeries`, no unbounded history, no static/shared state; each instance
  is independent.
- Numerical policy followed exactly: `update(BigDecimal close)` converts
  once via `close.doubleValue()`; the running sum and the returned average
  are `double` throughout; no `BigDecimal` arithmetic and no rounding
  inside the indicator.
- Readiness: not ready until `period` closes have been received; ready
  immediately on the `period`-th close. `value()` before readiness throws
  `IllegalStateException`, per the `Indicator` contract.
- The constructor also rejects `period < 1` directly (defense in depth;
  `IndicatorSpec` already enforces this before an `SMA` is ever
  constructed from a spec).
- 9 new tests in `SimpleMovingAverageTest`: the hand-calculated SMA(3)
  sequence on closes 1..6 (not ready, not ready, 2, 3, 4, 5); becomes
  ready exactly on the 3rd close; the window keeps rolling correctly past
  ten updates (last three of 1..10 average to 9); `value()` before
  readiness throws at every point before the period is filled; period-1
  tracks the latest close exactly; repeated identical values return that
  value; decimal closes (1.1, 2.2, 3.3 → 2.2) within a `1e-9` tolerance;
  two same-period instances are independent; period 0 is rejected.

### Engine implementation batch 4: `ExponentialMovingAverage`

- `in.vedchangani.parallax.engine.indicator.ExponentialMovingAverage`: not
  ready until `period` closes are received; on the `period`-th close, seeds
  from the SMA of those closes (per D-11) — not the first close, not the
  last, and not a recurrence applied from the start. `alpha = 2 /
  (period + 1)`; each close after the seed applies
  `ema = alpha * close + (1 - alpha) * previousEma`.
- Memory: a `double[period]` buffer plus a running sum during warm-up
  (O(period)); once seeded, the buffer is discarded (set to `null`) and
  only the scalar `ema` is kept (O(1) thereafter).
- Numerical policy followed exactly: `close.doubleValue()` is the only
  conversion; the seed sum, alpha, and EMA are all `double`; no
  `BigDecimal` in the calculation path.
- Constructor rejects `period < 1` directly, as defense in depth
  alongside `IndicatorSpec`'s existing validation.
- 11 new tests in `ExponentialMovingAverageTest`: the hand-calculated
  EMA(3) sequence on closes 1..6 (not ready, not ready, seed 2, then 3, 4,
  5); readiness exactly on the Nth close; the seed independently checked
  against SMA(2,4,6,8)=5 (period 4); a targeted correctness test using
  closes 0, 0, 30 (SMA seed 10) followed by three 100s — the wrong-seed
  alternatives (first close 0, or last close 30) diverge to 50 or 65
  instead of the correct 55, and the sequence is carried three steps
  further to also catch a wrong *recurrence* that would resync with the
  correct trajectory after one step; the recurrence checked independently
  with period 2 against `previous + alpha*(close-previous)` computed
  inline; period 1 (`alpha = 1`, reduces to the latest close); repeated
  identical values; decimal closes within `1e-9` tolerance; `value()`
  before readiness throwing `IllegalStateException` at every warm-up step;
  two same-period instances independent; period 0 rejected.

### Engine implementation batch 5: `RelativeStrengthIndex`

- `in.vedchangani.parallax.engine.indicator.RelativeStrengthIndex`: Wilder
  RSI. The first close only establishes `previousClose` and is never
  counted as a change, so RSI(period) becomes ready after `period + 1`
  closes — one close to establish `previousClose`, then `period` price
  changes. Initial `averageGain`/`averageLoss` are the simple means of the
  first `period` gains/losses; every change after that applies Wilder
  smoothing: `avg = (previousAvg * (period - 1) + current) / period`.
  `RSI = 100 - 100 / (1 + averageGain / averageLoss)`.
- Edge cases: `averageLoss = 0` is handled explicitly — 100 if
  `averageGain > 0`, else 50 (flat market). The remaining approved case,
  `averageGain = 0` and `averageLoss > 0`, needed no special branch: the
  formula already yields exactly 0 (`RS = 0` → `100 - 100/1 = 0`).
- State/memory: only `previousClose`, the warm-up gain/loss running sums
  (or, after seeding, the running averages), and a change counter — O(1)
  throughout. No buffer of individual gains/losses is needed even during
  warm-up, since Wilder's initial average only needs their sum. No
  `BarSeries`, no static/shared state.
- Constructor rejects `period < 1` directly; `IndicatorSpec` already
  rejects `RSI` with `period < 2` before an `RSI` instance is ever
  constructed from a spec, so period 1 is not separately re-validated
  here (per the batch instructions).
- 10 new tests in `RelativeStrengthIndexTest`, matching the fixtures in
  the batch instructions exactly: readiness boundary (not ready through
  the 3rd close on closes 100/101/102, ready after the 4th at 103);
  monotonic increase → RSI 100; flat market → RSI 50; monotonic decrease →
  RSI 0; the mixed fixture 100/102/101/103 → hand-calculated RSI 80
  (avgGain 4/3, avgLoss 1/3, RS 4); one further close (105) verifying
  Wilder smoothing continues correctly → RSI 87.5 (avgGain 14/9, avgLoss
  2/9, RS 7); `value()` throwing `IllegalStateException` at every
  pre-readiness point; period 0 rejected; two same-period instances
  independent (one trending up, one flat); repeated/flat values during
  warm-up followed by a real gain, confirming no spurious gain/loss
  carried over from the flat run.

## Current Architecture

```
Parallax/
├── pom.xml                  # root Maven aggregator (packaging=pom)
├── mvnw, mvnw.cmd, .mvn/    # Maven wrapper
├── engine/                  # plain Java engine module
│   ├── pom.xml
│   └── src/{main,test}/java/in/vedchangani/parallax/engine/
│       ├── data/            # Bar, BarSeries (implemented)
│       └── indicator/       # IndicatorType, IndicatorSpec, Indicator,
│                             #   SimpleMovingAverage, ExponentialMovingAverage,
│                             #   RelativeStrengthIndex (implemented)
├── backend/                 # Spring Boot application module
├── frontend/                # React/Vite application
├── docs/
│   ├── architecture.md
│   ├── decisions.md
│   └── progress.md
└── CLAUDE.md
```

Approved engine (not implemented): stateless
`Backtester.run(BarSeries, StrategyDefinition, BacktestConfig) ->
BacktestResult`; single-pass loop per bar: stop after `endDate` → execute
pending order at open → update indicators at close → skip lookback bars →
record equity point → evaluate strategy (ready, not last in-range bar) →
size at close and queue order. Future packages: `data`, `indicator`,
`strategy`, `execution`, `portfolio`, `result`, with `Backtester` at the
engine root.

## Verification

Run from `C:\Parallax`:

- `./mvnw -pl engine test`: `Tests run: 65, Failures: 0, Errors: 0, Skipped:
  0` — `BarTest` (13), `BarSeriesTest` (9), `EngineSmokeTest` (1),
  `IndicatorSpecTest` (12), `SimpleMovingAverageTest` (9),
  `ExponentialMovingAverageTest` (11), `RelativeStrengthIndexTest` (10, new
  this batch).
- `./mvnw clean install`: BUILD SUCCESS. Reactor: Parallax (pom), Parallax
  Engine, backend; same 65 engine tests plus 1 backend test.
- `git status --short`: only `RelativeStrengthIndex.java` and
  `RelativeStrengthIndexTest.java` are new; `backend/` and `frontend/` are
  unchanged.
- Docs consistency check (prior milestone): every D-n reference resolves to
  a heading in `decisions.md`; all progress references point to
  `docs/progress.md`; no terms from the superseded draft design remain.

## Decisions

- D-1 to D-3: modular monolith, root Maven aggregator, root Maven wrapper.
- D-4 to D-16 (approved, frozen): fail-fast market data; stateless
  Backtester with metrics outside the loop; inclusive range with separate
  lookback; sizing at N close with whole-order rejection at N+1 open; range
  boundaries without forced liquidation; market orders only; structured
  strategies; incremental indicators; fills as truth with SignalEvent on
  Fill and derived trades; average-cost accounting with the equity
  identity; BigDecimal ledger / double indicators; determinism and lookback
  provenance; long-only without an Instrument abstraction.

See [decisions.md](decisions.md).

## Known Problems

- Root `README.md` written during bootstrap is no longer in the repository.
  Confirm whether that was intended.

## Open Questions (edge cases not specified by the approved design)

1. `OrderRejection` for `ZERO_QUANTITY`: what `requiredCash` means when no
   order quantity exists (for example, the cost of one share at the sizing
   price plus commission).
2. SELL when `cash + quantity × fillPrice − commission < 0` (commission
   larger than the whole account): fail the run, or reject? Only reachable
   with degenerate commission settings; config validation could forbid it.
3. Rejection date convention: `ZERO_QUANTITY` is dated at the signal bar,
   `INSUFFICIENT_CASH` at the execution bar. Confirm.
4. Price adjustment basis (raw vs split/dividend adjusted) for datasets:
   to decide with the data layer; must be part of dataset provenance.

## Next Milestone

Consolidated indicator review and `IndicatorSnapshot` design: with SMA,
EMA, and RSI all implemented, decide how a strategy is handed a read-only
view of indicator values as of bar N close (per the approved architecture,
`IndicatorSnapshot` is the only market-derived input visible to strategy
evaluation — it must not expose the indicators themselves, `BarSeries`, or
any mutable run state). No `Strategy`, `Condition`, `Backtester`,
execution, or portfolio logic in that batch.