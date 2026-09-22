# Parallax Project Progress

## Current Milestone

`IndicatorType`, `IndicatorSpec`, and the `Indicator` runtime interface
implemented in `engine.indicator`, with focused tests.

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

## Current Architecture

```
Parallax/
├── pom.xml                  # root Maven aggregator (packaging=pom)
├── mvnw, mvnw.cmd, .mvn/    # Maven wrapper
├── engine/                  # plain Java engine module
│   ├── pom.xml
│   └── src/{main,test}/java/in/vedchangani/parallax/engine/
│       ├── data/            # Bar, BarSeries (implemented)
│       └── indicator/       # IndicatorType, IndicatorSpec, Indicator (implemented)
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

- `./mvnw -pl engine test`: `Tests run: 35, Failures: 0, Errors: 0, Skipped:
  0` — `BarTest` (13), `BarSeriesTest` (9), `EngineSmokeTest` (1),
  `IndicatorSpecTest` (12, new this batch).
- `./mvnw clean install`: BUILD SUCCESS. Reactor: Parallax (pom), Parallax
  Engine, backend; same 35 engine tests plus 1 backend test.
- `git status --short`: only the new `engine/src/.../indicator/` files are
  added; `backend/` and `frontend/` are unchanged. `.gitignore` shows a
  pending modification (adding `CLAUDE.md`) from outside this batch — not
  touched here.
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

SMA implementation with hand-calculated tests (the first concrete
`Indicator`). No EMA, RSI, strategy, execution, or portfolio logic in that
batch.