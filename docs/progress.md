# Parallax Project Progress

## Current Milestone

D-22 implementation: `Portfolio` and `EquityPoint`. Implemented: `Bar`,
`BarSeries`, `IndicatorType`, `IndicatorSpec`, `Indicator`,
`SimpleMovingAverage`, `ExponentialMovingAverage`, `RelativeStrengthIndex`,
`IndicatorSnapshot`, `Operand`, `Operator`, `Condition`, `PositionSizing`,
`StrategyDefinition`, `SignalType`, `SignalEvent`, `OrderSide`, `Order`,
`Fill`, `RejectionReason`, `OrderRejection`, `Portfolio`, `EquityPoint`.
`Portfolio` is the engine's first mutable type; every type before it is
immutable. Not yet implemented: `Trade`, `BacktestConfig`/
`BacktestResult`, and `Backtester`.

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

### Engine implementation batch 6: `IndicatorSnapshot`

- `in.vedchangani.parallax.engine.indicator.IndicatorSnapshot`: immutable
  record (`LocalDate date`, `BigDecimal close`,
  `Map<IndicatorSpec, Double> values`), implementing the frozen design from
  the prior checkpoint. It is the only market-derived input visible to
  strategy evaluation: no `Bar`, symbol, OHLV, `Portfolio` state, pending
  order, or clock timestamp; no runtime `Indicator` object can be stored,
  since the type has no field or constructor parameter that accepts one.
- A snapshot holds only ready indicator values (option A from the design
  checkpoint); "not ready" has no representation in this type. Building a
  snapshot is the caller's (later, the Backtester's) responsibility, once
  every referenced indicator is ready.
- Compact constructor validation: `date`, `close`, and `values` non-null;
  every key and value non-null; every value finite (`Double.isFinite`),
  rejecting NaN, +Infinity, and -Infinity with `IllegalArgumentException`
  naming the offending spec. `NullPointerException` for null
  date/close/map/key/value, matching the existing convention in
  `Bar`/`BarSeries`/`IndicatorSpec`.
- Canonical immutable map: the caller's map is copied into a fresh
  `TreeMap` ordered by a private `Comparator<IndicatorSpec>`
  (`IndicatorType` declaration order, then period ascending), then wrapped
  with `Collections.unmodifiableMap`. `IndicatorSpec` was not made
  `Comparable`, per the approved design — the comparator stays private to
  the snapshot. An empty map is valid.
- Lookup: `double value(IndicatorSpec spec)`. Null spec throws
  `NullPointerException`; an absent spec throws `IllegalArgumentException`
  naming the missing spec and the available keys; a present spec returns
  the stored `double`. Never null, 0, NaN, or `Optional`.
- Equality/hashCode/toString: default record semantics, no overrides.
  Because the stored map is always normalized to canonical order first,
  two snapshots built from equal content in different insertion order are
  `.equals()`, hash-equal, and print identically. `BigDecimal` scale is not
  normalized (100.0 and 100.00 are unequal), matching `Bar`.
- 25 new tests in `IndicatorSnapshotTest`: valid snapshot exposure; SMA(5)
  vs SMA(20) lookup; equal-but-separately-constructed spec as a key;
  missing-spec and null-spec lookup; empty map validity; null
  date/close/map/key/value rejection; NaN/+Infinity/-Infinity rejection
  (parameterized); defensive copy (mutating the caller's source map after
  construction does not affect the snapshot); `values()` unmodifiability
  (put/remove/clear); runtime isolation (updating a real
  `SimpleMovingAverage` after snapshot creation leaves the snapshot's
  stored value unchanged); insertion order not affecting equality,
  hashCode, or `toString()`; canonical iteration order; snapshots differing
  by date, close, or one indicator value; `BigDecimal` scale equality
  semantics; and a reflection check that the record's components are
  exactly `(LocalDate, BigDecimal, Map)`, with no `Indicator`-assignable
  component type.

### Documentation cleanup

- `docs/architecture.md`: corrected the stale `in.vedchangani.engine.*`
  future-package listing to `in.vedchangani.parallax.engine.*` (the actual
  root package, corrected earlier for the implemented classes but not yet
  updated in this listing), and corrected the stale "no engine code exists
  yet" status line under "Engine Architecture (V1..." to reflect that
  `data` and `indicator` (including `IndicatorSnapshot`) are implemented.

### Strategy grammar design checkpoint: `Operand` and `Condition` (design)

Design only, recorded before implementation. Full design is in
`docs/architecture.md` ("Operand and Condition") and D-18.

- Docs corrections made at this checkpoint:
  - Added the approved `IndicatorSnapshot` decision as D-17 to
    `decisions.md`. It had been missed in the earlier checkpoint.
  - Added a short `IndicatorSnapshot` summary to `architecture.md`.
  - Changed the `decisions.md` header, which said D-4 onward were "not
    yet implemented", to point to this file for implementation status.

### Engine implementation batch 7: `Operand`, `Operator`, `Condition`

`in.vedchangani.parallax.engine.strategy`, implementing D-18:

- `sealed interface Operand { double resolve(IndicatorSnapshot) }` with
  nested records `IndicatorRef(IndicatorSpec)`, `Close()` and
  `Constant(double)`. `IndicatorRef` resolves via `snapshot.value(spec)`
  and holds nothing but the spec — no runtime `Indicator`. `Close` holds no
  fields, so it exposes nothing but the snapshot's close. `Constant` is
  `double`, per the existing numerical policy (D-14); it rejects
  non-finite values and normalizes `-0.0` to `0.0`.
- `enum Operator { GT, LT }`.
- `sealed interface Condition { boolean evaluate(IndicatorSnapshot) }` with
  nested records `Compare(Operand, Operator, Operand)`,
  `All(List<Condition>)` and `Any(List<Condition>)`. `All`/`Any` nest,
  short-circuit (verified with a "poison" child that throws if evaluated,
  since `Condition` is sealed and cannot be mocked), and evaluate in list
  order.
- Evaluation is pure and snapshot-only: every method takes only an
  `IndicatorSnapshot`, so nothing in the grammar can reach a `Bar`,
  `BarSeries`, runtime `Indicator`, `Portfolio`, order, or the clock.
- Construction validation, exactly as D-18 specifies: null components
  throw NPE; a non-finite `Constant` throws IAE; an empty `All`/`Any`
  throws IAE and a null child throws NPE (via `List.copyOf`); single-child
  groups are allowed.
  - **`Compare` does not reject constant-vs-constant or identical
    operands.** The design checkpoint had rejected both; the
    implementation batch removed that restriction, since both are valid,
    deterministic expressions whose result simply does not depend on the
    snapshot — see the D-18 "Implementation update" and
    `docs/architecture.md`.
- A missing spec propagates the snapshot's `IllegalArgumentException`
  unchanged; the grammar does not duplicate that lookup validation.
- Equality is default structural record equality; `All`/`Any` child order
  is significant and is never canonicalized.
- 53 new tests across six files: `OperandTest` (10), `CompareTest` (16,
  including constant-vs-constant and identical-operand cases),
  `AllTest` (9), `AnyTest` (9), `ConditionPurityAndEqualityTest` (5,
  purity, snapshot non-mutation, structural equality including nested
  trees, order-sensitivity), `StrategyGrammarStructureTest` (4, a
  reflection check that no `Operand`/`Condition` record component is
  assignable from `Bar`, `BarSeries`, or `Indicator`, plus a check that
  both interfaces are sealed).
- Docs updated to match the implemented (not the originally rejected)
  behavior: `architecture.md`'s validation table and its status line;
  `decisions.md` D-18's validation list, "Why", and "Rejected" sections,
  with an explicit "Implementation update" note; this file.

### Cleanup: RSI runtime hardening and stale documentation

- `RelativeStrengthIndex`'s constructor now rejects `period < 2` directly,
  matching D-11/`IndicatorSpec`'s existing `RSI period >= 2` rule. Before
  this, only `IndicatorSpec` enforced the minimum; the concrete runtime
  class itself accepted `period == 1`. The RSI algorithm, readiness, and
  Wilder calculations are unchanged.
- `RelativeStrengthIndexTest`: `rejectsAPeriodBelowOne` renamed to
  `rejectsAPeriodBelowTwo` and extended to assert `RSI(1)` is rejected
  (previously only `RSI(0)` was tested at the runtime level); added
  `acceptsAPeriodOfTwo` to prove `RSI(2)` is accepted by the concrete
  class itself, not only by `IndicatorSpec`.
- `docs/architecture.md`: the "Engine Architecture" heading still read
  "not yet implemented" despite its own status line correctly listing
  `data`, `indicator`, and `strategy` as implemented; corrected to
  "partially implemented".
- `docs/progress.md`: removed the stale "Known Problems" entry about the
  root `README.md` — its removal was a deliberate choice by the project
  owner, not an open problem. Current Milestone rewritten to list the full
  set of implemented types instead of naming only the most recent batch.

### D-18 strategy grammar hardening

- `Condition.Compare.evaluate` now rejects a null snapshot with
  `NullPointerException`, even when neither operand reads it (for example
  constant vs constant). `Compare` is the only leaf of the sealed
  hierarchy, and a non-empty `All`/`Any` always evaluates its first child,
  so this single check covers every condition tree. Behavior for non-null
  snapshots is unchanged.
- Tests strengthened (5 new, 149 engine tests total):
  - `StrategyGrammarStructureTest`:
    - asserts the exact permitted subclasses of `Operand`
      (`IndicatorRef`, `Close`, `Constant`) and `Condition` (`Compare`,
      `All`, `Any`), and `Operator.values() == [GT, LT]`
    - replaces the blacklist check with a whitelist: every record component
      must be `IndicatorSpec`, `double`, `Operand`, `Operator` or `List`,
      and every `List` must be `List<Condition>`
  - `AllTest`/`AnyTest`: a new test proves `POISON` really throws when
    reached (`All(TRUE, POISON)` and `Any(FALSE, POISON)` both throw), so
    the short-circuit tests (`All(FALSE, POISON)` → false,
    `Any(TRUE, POISON)` → true) can't pass vacuously.
  - `CompareTest`:
    - close `100.10` vs `Constant(100.1)`: both GT and LT are false, which
      pins D-18's double-comparison boundary
    - null snapshot with constant vs constant throws NPE
  - `OperandTest`:
    - a missing spec's exception message names the spec, proving the
      snapshot's exception passes through untranslated
    - `Constant(-0.0)` and `Constant(0.0)` have equal hash codes

### Engine implementation batch: `PositionSizing` and `StrategyDefinition` (D-19)

- `in.vedchangani.parallax.engine.strategy.PositionSizing`: sealed
  interface with one V1 implementation, `CashFraction(BigDecimal
  fraction)`. Validates `0 < fraction <= 1` with `compareTo`, then
  normalizes the stored value with `stripTrailingZeros()` so `0.5` equals
  `0.50` and `1` equals `1.00`. It requests a cash fraction only — no
  quantity, commission, slippage, or affordability logic, which stays a
  Backtester/execution concern (D-7).
- `in.vedchangani.parallax.engine.strategy.StrategyDefinition`: immutable
  record `(Condition entryCondition, Condition exitCondition,
  PositionSizing positionSizing)`. Compact constructor rejects only null
  components; nothing else is validated, so Close-only/Constant-only
  conditions, no-indicator strategies, identical entry/exit conditions,
  shared specs, and multiple periods of one indicator type are all legal.
- `requiredIndicatorSpecs()`: a private static recursive walk over the
  sealed `Condition`/`Operand` hierarchies (`Compare` → both operands,
  `All`/`Any` → every child, `IndicatorRef` → its spec,
  `Close`/`Constant` → nothing), with **no `default` branch** in either
  `switch` — a future grammar addition is a compile error here until
  discovery is updated for it. Specs are collected into a `TreeSet`
  ordered by a private comparator identical in shape to
  `IndicatorSnapshot`'s (type declaration order, then period ascending),
  and returned as `List.copyOf(...)`: distinct, canonically ordered,
  unmodifiable. It is computed on every call, not stored, so it cannot
  drift from the conditions and does not participate in
  `StrategyDefinition` equality (which stays default record equality over
  the three components).
- 39 new tests across four files (188 engine tests total):
  `CashFractionTest` (10 — bounds, null, trailing-zero equality/hashCode,
  normalized storage); `StrategyDefinitionTest` (25 — construction,
  null-component rejection, structural equality, and
  `requiredIndicatorSpecs()` discovery: single/shared specs, multiple
  periods of one type, both `Compare` operands, several levels of nesting,
  Close/Constant contributing nothing, no indicators at all, order
  independent of authoring/traversal order, an unmodifiable result, and a
  cross-check that the order matches `IndicatorSnapshot`'s canonical order
  for the same specs); `StrategyDefinitionStructureTest` (4 — component
  types, `PositionSizing`'s exact permitted subclass, `CashFraction`'s
  single `BigDecimal` component, `requiredIndicatorSpecs()`'s return
  type).
- Fixed a test-authoring bug found during this batch (not a production
  bug): an early draft of `indicatorOnBothSidesOfOneCompareIsDiscovered`
  used the shared `EXIT` fixture, which itself references `RSI(14)`,
  producing three specs instead of the intended two. Corrected to use a
  single self-contained condition for both entry and exit.

### Engine implementation batch: `SignalEvent` and `Order` (D-20)

- `in.vedchangani.parallax.engine.strategy.SignalType`: enum, exactly
  `ENTER`/`EXIT`.
- `in.vedchangani.parallax.engine.strategy.SignalEvent`: immutable record
  `(SignalType type, IndicatorSnapshot snapshot)`. `date()` returns
  `snapshot.date()`, **derived, not stored** — this resolves the
  SignalEvent date-duplication question open since the `IndicatorSnapshot`
  batch. Holds no `StrategyDefinition`, `PositionSizing`, position/
  portfolio state, `Order`, or runtime `Indicator`.
- `in.vedchangani.parallax.engine.execution` (new package):
  - `OrderSide`: enum, exactly `BUY`/`SELL`.
  - `Order`: immutable record `(int id, long quantity, SignalEvent
    signal)`. Validates `id >= 1`, `quantity > 0`, `signal` non-null.
    `side()` is **derived** from `signal.type()` via an exhaustive
    `switch` (`ENTER` → `BUY`, `EXIT` → `SELL`) rather than stored, so an
    order can never disagree with the signal that produced it. No
    creation date, reference close, execution date, price, commission,
    slippage, or status field — all either recoverable from `signal` or
    future information at creation time. The Backtester will own order-id
    assignment (`int nextOrderId = 1`, incremented only when an `Order` is
    actually created) and pending-order state; `Order` itself validates
    only `id >= 1`.
- 28 new tests across four files (216 engine tests total):
  `SignalEventTest` (10 — construction, derived `date()`, null-component
  rejection, structural equality, exact snapshot preservation, runtime
  isolation via a real `SimpleMovingAverage`, `SignalType.values()`);
  `SignalEventStructureTest` (1 — components are exactly
  `(SignalType, IndicatorSnapshot)`); `OrderTest` (15 — construction,
  explicit sequential ids, id/quantity bounds, null-signal rejection,
  `side()` derivation for both signal types, structural equality,
  `OrderSide.values()`); `OrderStructureTest` (2 — components are exactly
  `(int, long, SignalEvent)`, and `OrderSide` is confirmed absent as a
  component, proving `side()` is derived).

### Engine implementation batch: `Fill` and `OrderRejection` (D-21)

- `in.vedchangani.parallax.engine.execution.Fill`: immutable record
  `(int orderId, LocalDate date, long quantity, BigDecimal referenceOpen,
  BigDecimal fillPrice, BigDecimal commission, SignalEvent signal)`.
  `orderId` and `signal` are **copied** from the originating `Order` (the
  `Order` object itself is never referenced — D-12). `date` is the
  execution bar's date, `referenceOpen` its actual open, `fillPrice` the
  open already adjusted for slippage by the execution step (not
  calculated here). `side()` derives `OrderSide.forSignal(signal.type())`
  and `slippageCost()` derives `|fillPrice − referenceOpen| × quantity`
  exactly — neither is stored, so a `Fill` can never disagree with its own
  signal or record an inconsistent slippage figure. Validates
  `orderId >= 1`, positive quantity/prices, non-negative commission, and
  non-null fields; does not validate slippage-rate correctness, cash
  sufficiency, or execution timing (Backtester concerns).
- `in.vedchangani.parallax.engine.execution.RejectionReason`: enum,
  exactly `ZERO_QUANTITY`/`INSUFFICIENT_CASH`.
- `in.vedchangani.parallax.engine.execution.OrderRejection`: sealed
  interface (`signal()`, `date()`, `reason()`) with exactly two records,
  chosen over one record with nullable fields because the two cases carry
  genuinely different information:
  - `ZeroQuantity(SignalEvent signal)`: no order ever existed, so no
    order id, quantity, or cash fields exist to carry. Requires an
    `ENTER` signal. `date()` derives `signal.date()` (nothing happens
    between signal and rejection); `reason()` derives `ZERO_QUANTITY`.
  - `InsufficientCash(int orderId, LocalDate date, long quantity,
    BigDecimal requiredCash, BigDecimal availableCash, SignalEvent
    signal)`: an order existed and consumed `orderId`, which is why fill
    IDs can show gaps. `date` is the execution bar's date — stored, not
    derived, because it genuinely differs from the signal date. Requires
    an `ENTER` signal and `requiredCash.compareTo(availableCash) > 0`
    (checked in the constructor, so an instance can never claim
    insufficiency while showing enough cash); `reason()` derives
    `INSUFFICIENT_CASH`.
- `OrderSide` gained `static OrderSide forSignal(SignalType type)`
  (`ENTER → BUY`, `EXIT → SELL`, exhaustive switch, null-checked);
  `Order.side()` now delegates to it instead of its own inline switch.
  `Order`'s record components and validation are unchanged.
- This resolves all three of D-20's open Fill/OrderRejection questions
  (order-ID presence, order-ID gap semantics, copy-vs-reference) plus two
  older open questions: OQ1 (`ZERO_QUANTITY` has no `requiredCash` field
  at all, rather than a sentinel) and OQ3 (`ZeroQuantity` derives the
  signal date; `InsufficientCash` stores its own, genuinely different,
  execution date).
- 54 new tests across four files (270 engine tests total): `FillTest`
  (22 — every stored field, BUY/SELL side derivation, slippage cost for
  both sides and the zero-slippage case, zero commission accepted, every
  null/numeric validation boundary, equality, and explanation recovery
  without an `Order` object); `FillStructureTest` (3 — exact component
  shape, no `Order`/`OrderSide` component, no stored slippage-cost
  component alongside the derived method); `OrderRejectionTest` (24 —
  `RejectionReason.values()`, `ZeroQuantity` construction/date/reason/
  EXIT-rejection/equality, `InsufficientCash` construction/reason/
  independent date/every validation boundary including the
  `requiredCash > availableCash` check at, above, and below the
  boundary/EXIT-rejection/equality); `OrderRejectionStructureTest` (5 —
  exact permitted-subclass set, exact component shapes for both records,
  a whitelist check that neither record holds an `Order`, `Bar`,
  `BarSeries`, runtime `Indicator`, or collection, and `OrderSide`'s exact
  values).
- Fixed a test-authoring bug found while writing `FillStructureTest` (not
  a production bug): `Set.of(...)` throws on duplicate elements, and
  `Fill` has three `BigDecimal` components, so the original
  "no forbidden component type" check threw `IllegalArgumentException`
  before it could assert anything. Corrected to collect component types
  into a `Set` via a stream collector, which tolerates duplicates.

### Engine implementation batch: `Portfolio` and `EquityPoint` (D-22)

- `in.vedchangani.parallax.engine.portfolio.Portfolio`: the engine's
  **first mutable type** — a `final class` with exactly four private
  fields (`cash`, `quantity`, `costBasis`, `realizedPnl`), per-run, never
  shared. `Portfolio(BigDecimal initialCash)` requires non-null and
  `>= 0`; starts flat. `apply(Fill)` is the only mutator, all-or-nothing:
  every precondition is checked before any field changes, so a rejected
  operation is provably a no-op.
  - **BUY**: rejected unless flat and unless affordable
    (`q×f + c <= cash`); otherwise `cash -= totalCost`,
    `quantity = q`, `costBasis = totalCost` (BUY commission included,
    per D-13).
  - **SELL**: rejected unless long and `fill.quantity() == quantity`
    exactly (V1's single full-exit rule, which alone blocks partial
    exits, over-selling, and negative positions — D-16), and unless the
    resulting cash would stay `>= 0` (the fail-fast backstop for OQ2);
    otherwise `proceeds = q×f − c`, `cash += proceeds`,
    `realizedPnl += proceeds − costBasis`, `quantity = 0`,
    `costBasis = 0`.
  - `markToMarket(date, close)` validates its arguments and returns a new
    `EquityPoint` from the current state — it never mutates, and the
    close is never stored.
  - No `averageCost()`: per your explicit choice, average cost stays out
    of the engine entirely, since `costBasis / quantity` would be the
    engine's only division.
- `in.vedchangani.parallax.engine.portfolio.EquityPoint`: immutable
  record `(LocalDate date, BigDecimal cash, long quantity, BigDecimal
  costBasis, BigDecimal realizedPnl, BigDecimal close)`. Derives
  `marketValue()` (`close × quantity`), `equity()`
  (`cash + marketValue()`), and `unrealizedPnl()`
  (`marketValue() − costBasis`) — none stored, so none can disagree with
  the four owned fields. Validates non-null fields, `cash >= 0`,
  `quantity >= 0`, `close > 0`, and flat ⇔ zero cost basis.
- **`PortfolioState` removed** from the architecture: it would have
  exactly duplicated the last `EquityPoint`'s fields.
- Because V1 has no partial exits, `removedBasis` (D-13) always equals
  the full `costBasis` exactly — **D-14's reserved partial-basis
  `MathContext` is unused in V1**, and nothing in `Portfolio`/
  `EquityPoint` divides or rounds.
- 41 new tests across three files (311 engine tests total):
  `PortfolioTest` (22 — initial state; BUY accounting at three marked
  closes; winning and losing exits; zero- and non-zero-commission round
  trips; two accumulated round trips; six rejected-operation cases each
  asserting the state is byte-for-byte unchanged afterwards; invalid
  constructor/`apply`/`markToMarket` arguments; `markToMarket`
  non-mutation across repeated calls); `EquityPointTest` (15 — derived
  values when long and when flat, every validation boundary, negative
  `realizedPnl` accepted, equality); `PortfolioStructureTest` (4 — exact
  field types with no static state, exact record component shapes, no
  stored derived value on `EquityPoint`). Every test that produces an
  `EquityPoint` also asserts both accounting identities
  (`equity == cash + marketValue` and
  `equity == initialCapital + realizedPnl + unrealizedPnl`) via
  `compareTo`.
- Two design conflicts with already-approved rules were resolved with you
  before this batch, both via `AskUserQuestion`: (1) the checkpoint's
  "additional BUY while long" weighted-average scenario conflicts with
  D-16's no-pyramiding rule — you chose strict flat/long enforcement, so
  that scenario became a rejected-operation test instead; (2) whether to
  expose `averageCost()` — you chose to keep it out of the engine
  entirely, avoiding the engine's only division.

## Current Architecture

```
Parallax/
├── pom.xml                  # root Maven aggregator (packaging=pom)
├── mvnw, mvnw.cmd, .mvn/    # Maven wrapper
├── engine/                  # plain Java engine module
│   ├── pom.xml
│   └── src/{main,test}/java/in/vedchangani/parallax/engine/
│       ├── data/            # Bar, BarSeries (implemented)
│       ├── indicator/       # IndicatorType, IndicatorSpec, Indicator,
│       │                     #   SimpleMovingAverage, ExponentialMovingAverage,
│       │                     #   RelativeStrengthIndex, IndicatorSnapshot
│       │                     #   (implemented)
│       ├── strategy/        # Operand, Operator, Condition, PositionSizing,
│       │                     #   StrategyDefinition, SignalType, SignalEvent
│       │                     #   (implemented)
│       ├── execution/       # OrderSide, Order, Fill, RejectionReason,
│       │                     #   OrderRejection (implemented)
│       └── portfolio/       # Portfolio, EquityPoint (implemented)
├── backend/                 # Spring Boot application module
├── frontend/                # React/Vite application
├── docs/
│   ├── architecture.md
│   ├── decisions.md
│   └── progress.md
└── CLAUDE.md
```

Approved but not yet implemented — the run loop: stateless
`Backtester.run(BarSeries, StrategyDefinition, BacktestConfig) ->
BacktestResult`; single-pass loop per bar: stop after `endDate` → execute
pending order at open → update indicators at close → skip lookback bars →
record equity point → evaluate strategy (ready, not last in-range bar) →
size at close and queue order. Still to build: `Trade` (in `portfolio`),
`BacktestConfig`/`BacktestResult` (in `result`), and `Backtester` at the
engine root. The full non-strategy accounting core
(`SignalEvent`/`Order`/`Fill`/`OrderRejection`/`Portfolio`/`EquityPoint`)
is now implemented.

## Verification

Run from `C:\Parallax` (current state, after the D-22 implementation
batch):

- New tests (`PortfolioTest`, `EquityPointTest`, `PortfolioStructureTest`):
  41 tests, 0 failures.
- `./mvnw -pl engine test`: `Tests run: 311, Failures: 0, Errors: 0,
  Skipped: 0` — `BarTest` (13), `BarSeriesTest` (9), `EngineSmokeTest` (1),
  `IndicatorSpecTest` (12), `SimpleMovingAverageTest` (9),
  `ExponentialMovingAverageTest` (11), `RelativeStrengthIndexTest` (11),
  `IndicatorSnapshotTest` (25), `OperandTest` (10), `CompareTest` (18),
  `AllTest` (10), `AnyTest` (10), `ConditionPurityAndEqualityTest` (5),
  `StrategyGrammarStructureTest` (5), `CashFractionTest` (10),
  `StrategyDefinitionTest` (25), `StrategyDefinitionStructureTest` (4),
  `SignalEventTest` (10), `SignalEventStructureTest` (1), `OrderTest`
  (15), `OrderStructureTest` (2), `FillTest` (22), `FillStructureTest`
  (3), `OrderRejectionTest` (24), `OrderRejectionStructureTest` (5),
  `PortfolioTest` (22), `EquityPointTest` (15), `PortfolioStructureTest`
  (4).
- `./mvnw clean install`: BUILD SUCCESS. Reactor: Parallax (pom), Parallax
  Engine, backend; 311 engine tests plus 1 backend test.
- `backend/` and `frontend/` unchanged.
- Docs consistency check: every `D-n` reference (`D-1` through `D-22`)
  resolves to a heading in `decisions.md`.

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
- D-17: `IndicatorSnapshot` is an immutable value that holds only ready
  values, in canonical order, with finite values and fail-fast lookup
  (implemented).
- D-18: `Operand`/`Condition` are sealed interfaces of nested records with
  GT/LT only, nestable `All`/`Any`, no empty groups, `double` constants,
  constant-vs-constant and identical operands permitted, and pure
  snapshot-only evaluation (implemented).
- D-19: `StrategyDefinition(entry, exit, sizing)` with structural-only
  validation; `PositionSizing.CashFraction` as the only V1 sizing mode,
  normalized so equal fractions are equal values;
  `requiredIndicatorSpecs()` derived on demand (not stored, not part of
  equality) via an exhaustive, no-default traversal, in the same
  canonical order as `IndicatorSnapshot` (implemented).
- D-20: `SignalEvent(type, snapshot)` with `date()` derived from the
  snapshot rather than stored; `Order(id, quantity, signal)` with
  `side()` derived from the signal type rather than stored; run-local
  sequential order ids owned by the Backtester; no status field on
  `Order` (implemented).
- D-21: `Fill` copies `orderId`/`signal` from its `Order` (never
  references the `Order` itself) and derives `side()`/`slippageCost()`;
  `OrderRejection` is sealed to `ZeroQuantity` (no order ever existed, no
  order id, date derived from the signal) and `InsufficientCash` (an
  order existed and kept its id, execution date stored since it genuinely
  differs, `requiredCash > availableCash` enforced at construction); both
  rejection records require an `ENTER` signal, since V1 has no SELL
  rejection; order-ID gaps in the fills are documented as expected,
  deterministic behavior (implemented).
- D-22: `Portfolio` enforces strict flat/long accounting (no pyramiding,
  full exits only) and owns exactly four fields (cash, quantity, cost
  basis, realized P&L), with no stored/computed `averageCost` anywhere in
  the engine; `apply(Fill)` is the only mutator and is all-or-nothing;
  `markToMarket` is pure; `EquityPoint` derives `marketValue`/`equity`/
  `unrealizedPnl` rather than storing them; `PortfolioState` is removed
  as a duplicate of the last `EquityPoint` (implemented).

See [decisions.md](decisions.md).

## Known Problems

None currently open.

## Open Questions (edge cases not specified by the approved design)

1. ~~`OrderRejection` for `ZERO_QUANTITY`: what `requiredCash` means when
   no order quantity exists.~~ **Resolved by D-21:**
   `OrderRejection.ZeroQuantity` has no `requiredCash` field at all — no
   order ever existed, so no order-shaped cost exists to name.
2. SELL when `cash + quantity × fillPrice − commission < 0` (commission
   larger than the whole account): fail the run, or reject? Only reachable
   with degenerate commission settings; config validation could forbid it.
   **Still open** — V1's `OrderRejection` model has no way to represent a
   SELL rejection (D-21), so this must be prevented by `BacktestConfig`
   validation, not solved by widening the outcome model. **D-22 adds the
   concrete fail-fast backstop:** `Portfolio.apply` now throws
   `IllegalStateException` if a SELL would leave cash negative, so the
   symptom `BacktestConfig` validation must prevent from ever being
   reached is now precisely named.
3. ~~Rejection date convention: `ZERO_QUANTITY` is dated at the signal
   bar, `INSUFFICIENT_CASH` at the execution bar.~~ **Resolved by D-21:**
   confirmed exactly as stated — `ZeroQuantity.date()` derives
   `signal.date()`; `InsufficientCash.date` is stored separately as the
   execution bar's date, since it's genuinely different information.
4. Price adjustment basis (raw vs split/dividend adjusted) for datasets:
   to decide with the data layer; must be part of dataset provenance.

5. ~~`SignalEvent(date, type, snapshot)` as sketched in `architecture.md`
   would duplicate `snapshot.date()`.~~ **Resolved by D-20:**
   `SignalEvent.date()` is derived from `snapshot.date()`, not stored.
6. ~~Where the set of referenced `IndicatorSpec`s is collected.~~
   **Resolved by D-19:** `StrategyDefinition.requiredIndicatorSpecs()`, a
   private traversal derived on demand, in canonical order.
7. Persisting `Constant(double)` in the backend: store it in a form that
   round-trips exactly (for example `Double.toString`) so that reloaded
   strategy versions stay equal.
8. Whether to later consolidate the canonical-order comparator into one
   shared constant used by both `IndicatorSnapshot` and
   `StrategyDefinition`, instead of the current two independent private
   copies of the same two-line rule (D-19's open question).
9. Identical entry and exit conditions are legal per D-19 but would churn
   (enter, then exit on the next evaluable bar). The engine will not
   reject them; a backend or UI warning may be wanted later.
10. ~~Whether an `INSUFFICIENT_CASH` `OrderRejection` should carry the
    rejected order's id.~~ **Resolved by D-21:** yes —
    `InsufficientCash.orderId` is exactly the id the order had already
    been assigned.
11. ~~Order ids can show gaps in the fills.~~ **Resolved by D-21:**
    documented as expected, deterministic behavior — `{fill IDs} ∪
    {InsufficientCash IDs}` is always exactly `{1..n}`, so every gap is
    explained by a matching rejection.
12. ~~Whether `Fill` copies `orderId` + `signal` or references the
    `Order` itself.~~ **Resolved by D-21:** copies, per D-12; the `Order`
    object is never referenced.
13. Where average cost is shown: confirmed to stay entirely out of the
    engine (D-22, your explicit choice). A later backend/reporting layer
    computes `costBasis / quantity` and chooses its own display precision.

## Next Milestone

`Trade` design — the immutable, derived-after-the-run record pairing an
entry fill and an exit fill (D-12), including how open trades (an entry
fill with no exit) are represented. Also where `BacktestConfig` finally
gets designed, since it owns the OQ2 fix (forbidding a `commission`/
`slippageRate` combination that could force `Portfolio` to reject a SELL
for negative cash). No `Backtester` or metrics logic in that batch.