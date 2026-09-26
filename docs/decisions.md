# Architectural Decisions

A concise decision register. D-1 to D-3 cover repository bootstrap; D-4
onward are the frozen V1 engine design. Decision numbers are stable and
never reused. A superseding decision references the one it revises rather
than editing its text. Full architectural detail: [architecture.md](architecture.md).
Implementation history: Git log.

## D-1 Modular monolith for V1, not microservices

**Decision:** Single deployable Spring Boot app with a framework-independent
engine module, not separate services.

**Why:** V1 has no independent-scaling or team-boundary need; microservices
would add infrastructure complexity and nondeterminism risk without
benefit. Engine independence is enforced via Maven module boundaries, not
a network boundary — cheap to split later if ever justified.

## D-2 Root Maven aggregator with `engine` and `backend` as modules

**Decision:** Root `pom.xml` (`packaging=pom`) aggregates `engine` and
`backend`. `backend` keeps `spring-boot-starter-parent` as its own Maven
parent; aggregation doesn't require a shared `<parent>`.

## D-3 Maven wrapper at repository root

**Decision:** `mvnw`/`mvnw.cmd`/`.mvn/` live at the repo root, not under
`backend/`, so the whole multi-module build runs from one wrapper.

## D-4 Invalid market data fails fast; no provider logic in the engine

**Decision:** `Bar`/`BarSeries` validate on construction (positive prices,
consistent high/low, non-negative volume, non-blank symbol, non-empty
series, strictly ascending dates). Invalid data is rejected before the run
starts — never reordered, deduplicated, repaired, or clamped. Providers
and normalization live in the backend.

**Why:** Repairing data in the engine would silently invent financial
assumptions.

## D-5 Stateless Backtester, single-pass loop, metrics outside the loop

**Decision:** `Backtester.run(series, strategy, config)` holds no
persistent fields; each run creates its own mutable state. Processing is
single-threaded, single-pass. Metrics are computed after the run, not
inside the loop.

**Why:** A fixed-order loop is verifiable top-to-bottom for look-ahead
bias; keeping metrics out of the loop separates simulation from analysis.

## D-6 Inclusive date range with separate lookback

**Decision:** `BacktestConfig` has inclusive `startDate`/`endDate`. Bars
before `startDate` are lookback: they update indicators only (no signals,
orders, fills, equity points). Bars after `endDate` are ignored. There is
no lookback-count field — lookback is whatever the series contains before
`startDate`.

**Consequence:** Results depend on where the supplied series begins (EMA/RSI
seeding), so the lookback portion is part of reproducibility (D-15).

## D-7 Sizing at bar N close; execution at N+1 open; whole-order rejection

**Decision:** A signal at bar N close sizes a BUY from N-close information
only: `floor((cash × fraction − commission) / (close_N × (1 +
slippageRate)))`. Quantity ≤ 0 → `ZERO_QUANTITY` rejection, no order
created. The order executes at the next available bar's open; if
`quantity × fillPrice + commission` exceeds available cash, the entire
order is rejected (`INSUFFICIENT_CASH`) — no partial fills, quantity never
reduced. SELL sells the full held position.

**Revised by D-23:** the sizing and affordability formulas gain an
exit-commission reserve. This paragraph and the decision above are the
original, unedited text; see D-23 for the revision.

## D-8 Range boundaries: no signal on the last in-range bar, no forced liquidation

**Decision:** The strategy is not evaluated on the last in-range bar, so
no order is ever pending at run end and no fill occurs outside
`[startDate, endDate]`. If `endDate` is a non-trading day, the last
available in-range bar is used. An open position at the end is marked to
the last in-range close, not liquidated. A request with no in-range bar is
rejected before execution.

## D-9 Market orders only in V1

**Decision:** Only market orders, executed at the next available bar's
open. No limit/stop orders (they need intrabar high/low fill rules — a
separate correctness problem).

## D-10 Structured strategies without a general expression language

**Decision:** `StrategyDefinition(entry, exit, sizing)`. Conditions are
`Compare` (`GT`,`LT`), `All`, `Any`. Operands are `IndicatorRef`,
`Constant`, `Close`. Only sizing mode: `CashFraction(0 < f <= 1)`. Entry
evaluated when flat, exit when long, only once every referenced indicator
is ready.

**Why:** Expresses V1 strategies as data, no user code. No `NOT`,
arithmetic, equality, `>=`/`<=`, or crossover until needed.

## D-11 Indicators are per-run incremental objects fed one close at a time

**Decision:** One runtime `Indicator` per distinct `IndicatorSpec`:
`update(BigDecimal close)`, `isReady()`, `value()`. Never sees `BarSeries`.
SMA(n)/EMA(n) ready after `n` closes (EMA seeded with SMA). RSI(n) uses
Wilder smoothing, ready after `n+1` closes; RSI = 100 when `avgLoss=0` and
`avgGain>0`, 50 when both are 0.

**Why:** An indicator fed only past closes cannot leak future data.

## D-12 Fills are the source of truth; SignalEvent is carried by Fill; trades are derived

**Decision:** Every `SignalEvent` has exactly one outcome: a `Fill` or an
`OrderRejection`, each carrying the `SignalEvent`. Orders are transient,
not part of the result. No separate signal list, no mutable open-trade
tracker. Trades are derived after the run from alternating BUY/SELL fills;
a trailing BUY is an open trade excluded from completed-trade statistics.

## D-13 Average-cost accounting with commissions in cost basis

**Decision:** `Portfolio` owns cash, quantity, cost basis (incl. buy
commission), realized P&L, mutated only by applying fills. SELL removes
`costBasis × q / currentQuantity`; proceeds are net of commission.
Unrealized P&L/equity are derived at a close; average cost is derived for
display only (never computed in-engine — see D-22). Identity
`equity = initialCapital + realizedPnl + unrealizedPnl` is tested at every
in-range equity point.

**Why:** Realized P&L is net of all trading costs; the identity catches
accounting drift.

## D-14 BigDecimal ledger values, double indicator values

**Decision:** `BigDecimal` for prices, commissions, slippage rate, cash
fraction, cash, cost basis, P&L, equity, initial capital — compared with
`compareTo`, never rounded to cents. `long` for quantities; `int` for
periods/order IDs; `double` for indicator/statistic calculations. A fixed
`MathContext` was reserved for the partial-basis division; unused in V1
(D-22 — no partial exits).

**Why:** Ledger values must reconcile exactly; indicators/statistics are
approximate by nature and Java double math is deterministic.

## D-15 Determinism and reproducibility requirements

**Decision:** Identical `BarSeries` content, `StrategyDefinition`,
`BacktestConfig`, and engine version produce identical results. No current
time, randomness, static mutable state, order-affecting parallelism,
hash-order dependence, or locale sensitivity. Order IDs sequential;
indicator creation order deterministic.

**Consequence:** Experiment persistence must eventually identify the exact
supplied series, including its lookback portion.

## D-16 Long-only, no leverage, no Instrument abstraction

**Decision:** One long position at a time, whole shares, no leverage, no
pyramiding, no partial exits, no multiple simultaneous positions. The
instrument is identified by the `BarSeries` symbol only.

## D-17 IndicatorSnapshot is an immutable, ready-only, canonically ordered value

**Decision:** `IndicatorSnapshot(LocalDate date, BigDecimal close,
Map<IndicatorSpec, Double> values)` in `engine.indicator` — what's known at
bar N close. Holds no `Bar`, symbol, OHLV, portfolio/order state, clock, or
runtime `Indicator`.

- Holds only ready values; "not ready" has no representation.
- Values must be finite (IAE for NaN/±Infinity); NPE for nulls.
- Caller's map copied into a `TreeMap` (private comparator: `IndicatorType`
  declaration order, then period) and exposed unmodifiable — deterministic
  iteration, unlike `Map.copyOf` (D-15). `IndicatorSpec` is not `Comparable`.
- `value(spec)`: NPE for null, IAE naming the spec if absent — never null/0/NaN/Optional.
- Default record equality; `BigDecimal` scale-sensitive, as in `Bar`.

**Why:** Strategies must see frozen values (runtime indicators are
mutable); the finite check is centralized here so a NaN can never
silently make every comparison false.

**Rejected:** passing mutable `Indicator` instances; `EnumMap` (can't hold
two periods of one type); `IndicatorValue` wrapper; positional arrays;
`Optional`/NaN for not-ready; carrying the whole `Bar`.

## D-18 Operand and Condition: two sealed interfaces of nested records, GT/LT only, no empty groups

**Decision:** V1 strategy grammar in `engine.strategy`:

- `sealed Operand { double resolve(IndicatorSnapshot) }`: `IndicatorRef(spec)`
  → `snapshot.value(spec)`; `Close()` → `snapshot.close().doubleValue()`;
  `Constant(double)` → itself (finite required, `-0.0` normalized to `0.0`).
- `enum Operator { GT, LT }`.
- `sealed Condition { boolean evaluate(IndicatorSnapshot) }`:
  `Compare(left, op, right)`; `All(List<Condition>)`/`Any(List<Condition>)`
  — short-circuit, nestable, non-empty (`List.copyOf`, IAE if empty).

Evaluation is a pure function of one snapshot — no `BarSeries`, `Bar`,
runtime `Indicator`, `Portfolio`, or clock reachable. `Compare` permits
constant-vs-constant and `left.equals(right)` (valid deterministic
expressions whose result just doesn't depend on the snapshot). A missing
spec at evaluation propagates the snapshot's own `IllegalArgumentException`
unchanged. Equality is structural; `All`/`Any` child order is significant
and never canonicalized. `Compare.evaluate` also rejects a null snapshot
directly (the one leaf of the sealed hierarchy every evaluation reaches).

**Why:** Look-ahead protection comes from the method signature, not
discipline. `double` constants match D-14 and avoid `BigDecimal` scale
issues. Rejecting empty groups avoids an arbitrary vacuous-truth
convention (`All([])` would always enter; `Any([])` would never trade).

**Rejected:** `BigDecimal` constants; external evaluator/visitor;
lambda/object operators (no reliable equality); canonicalizing child
order; depth limits; translating the missing-spec exception; `NOT`,
`>=`/`<=`/`==`, crossover, arithmetic/function operands (as D-10).

**Consequence:** Conditions are *state* conditions, not crossover events —
`SMA(20) > SMA(50)` means "is above". A strategy may enter mid-trend on
the first evaluable bar.

## D-19 StrategyDefinition, CashFraction sizing, and canonical IndicatorSpec discovery

**Decision:** `engine.strategy` adds:

- `sealed PositionSizing` → `CashFraction(BigDecimal fraction)`:
  `0 < fraction <= 1`, `stripTrailingZeros()`-normalized (`0.5` = `0.50`).
  A configuration value only — no quantity/affordability logic (that's D-7).
- `StrategyDefinition(entryCondition, exitCondition, positionSizing)`:
  components non-null only — structural validation, not a strategy-quality
  validator. Close-only conditions, no indicators, identical entry/exit,
  shared specs, multiple periods of one type are all legal.
- `requiredIndicatorSpecs()`: every `IndicatorSpec` referenced by either
  condition, distinct, in `IndicatorSnapshot`'s canonical order. Computed
  on demand by a private recursive walk (exhaustive `switch`, no `default`
  branch — a future grammar type fails to compile here until discovery is
  updated). Not stored, not part of equality.

**Why:** Discovery lives on `StrategyDefinition`, not on the D-18 grammar
or a separate collector class — it's a backtest-setup concern, and one
caller doesn't justify a new type. Canonical order (not tree/authoring
order) keeps indicator creation order and snapshot order in agreement.

**Rejected:** discovery methods on `Operand`/`Condition`; a visitor
framework; `IndicatorSpec implements Comparable`; storing
`requiredIndicatorSpecs()` as a component; other sizing modes; ID/name/
symbol/timeframe fields on `StrategyDefinition`.

**Open question:** whether to consolidate the canonical-order comparator
(currently duplicated privately in `IndicatorSnapshot` and
`StrategyDefinition`) into one shared constant — deferred until it's a
real maintenance problem.

## D-20 SignalEvent(type, snapshot) with derived date; Order(id, quantity, signal) with derived side

**Decision:**

- `engine.strategy`: `enum SignalType{ENTER,EXIT}`;
  `SignalEvent(SignalType type, IndicatorSnapshot snapshot)` — `date()`
  derives `snapshot.date()`, not stored.
- `engine.execution` (new package): `enum OrderSide{BUY,SELL}`;
  `Order(int id, long quantity, SignalEvent signal)` — `id>=1`,
  `quantity>0`, `signal` non-null; `side()` derives from `signal.type()`
  via exhaustive switch, not stored.

`Order` carries no creation date, reference close, execution date/price,
or status (all recoverable from `signal`, future info, or represented by
the eventual `Fill`/`OrderRejection`). `id` is run-local/sequential,
assigned by the Backtester only when an `Order` is actually created (a
`ZERO_QUANTITY` rejection consumes none); `Order` itself validates only
`id>=1`.

**Why:** Deriving date/side makes the corresponding invalid states
(a signal disagreeing with its own date; an order disagreeing with its own
signal) unrepresentable, rather than merely checked. `int` ID matches
D-14; no UUID/DB ID/static counter (would leak state across runs,
breaking D-5).

**Rejected:** stored date/side fields; a mutable `Order` with a status
enum; quantity/affordability logic inside `SignalEvent`/`Order`.

**Consequence:** `SignalEvent → sized (D-7) → Order → pendingOrder →
Fill`/`OrderRejection`, with `order.id()`/`order.signal()` carried
forward. Orders are never part of `BacktestResult`.

## D-21 Fill copies order data; OrderRejection is sealed to ZeroQuantity and InsufficientCash

**Decision:** `engine.execution` adds:

- `Fill(int orderId, LocalDate date, long quantity, BigDecimal
  referenceOpen, BigDecimal fillPrice, BigDecimal commission, SignalEvent
  signal)`: `orderId>=1`, quantity/prices positive, commission `>=0`, all
  non-null. `side()` and `slippageCost()` (`|fillPrice-referenceOpen| ×
  quantity`) derived, not stored. `orderId`/`signal` are **copied** from
  the originating `Order` — the `Order` object itself is never referenced
  (D-12).
- `enum RejectionReason{ZERO_QUANTITY,INSUFFICIENT_CASH}`.
- `sealed OrderRejection{signal();date();reason();}` with exactly two
  records (chosen over one nullable-field record — the two cases carry
  genuinely different information):
  - `ZeroQuantity(signal)`: no order ever existed, so no order id/cash
    fields. `date()` derives `signal.date()`. Requires an `ENTER` signal.
  - `InsufficientCash(orderId, date, quantity, requiredCash,
    availableCash, signal)`: `date` stored (genuinely differs from signal
    date); `requiredCash > availableCash` enforced at construction.
    Requires an `ENTER` signal.
- `OrderSide.forSignal(SignalType)` added; `Order.side()` delegates to it.

**Why:** Both rejection cases require `ENTER` because V1's SELL always
sells the full position and needs no cash, so no SELL rejection is
representable. Because only created orders consume IDs and each ends in
exactly one `Fill` or `InsufficientCash`, `{fill IDs} ∪
{InsufficientCash IDs} = {1..n}` always — a gap in fill IDs is expected,
deterministic, and explained by the matching rejection.

**Rejected:** `Fill` holding its `Order` directly; stored side/slippageCost;
one nullable-field rejection record; sentinel `requiredCash`/`availableCash`
for `ZeroQuantity`; a SELL rejection type; a generic `Outcome` wrapper
(the result's `fills`/`rejections` lists already express the invariant).

**Still open:** OQ2 — a degenerate config could in principle demand a SELL
rejection, which this model cannot represent; `BacktestConfig` must
prevent it (resolved by D-23).

## D-22 Portfolio enforces strict flat/long accounting; EquityPoint derives its P&L figures

**Decision:** `engine.portfolio` adds:

- `final class Portfolio` (mutable, per-run, never shared) — exactly four
  fields: `cash`, `quantity`, `costBasis`, `realizedPnl`.
  `Portfolio(BigDecimal initialCash)`: `>=0`, starts flat.
  `apply(Fill)` is the **only** mutator, all-or-nothing (every
  precondition checked before any field changes):
  - **BUY**: rejected (ISE) unless flat and `q×f+c <= cash`; else
    `cash -= total`, `quantity = q`, `costBasis = total` (commission in
    basis, per D-13).
  - **SELL**: rejected unless long, `fill.quantity()==quantity` exactly
    (blocks partial exits, over-selling, negative positions in one check
    — D-16), and resulting cash `>=0`; else `proceeds = q×f−c`,
    `cash += proceeds`, `realizedPnl += proceeds−costBasis`,
    `quantity=costBasis=0`.
  - `markToMarket(date, close)`: pure, returns a new `EquityPoint`; never
    mutates, never stores the close.
  - No `averageCost()` — kept out of the engine entirely (the one
    potential division; display-only per D-13).
- `EquityPoint(date, cash, quantity, costBasis, realizedPnl, close)`:
  derives `marketValue()`/`equity()`/`unrealizedPnl()`, none stored.
  Validates `cash>=0`, `quantity>=0`, `close>0`, flat ⇔ zero cost basis.
- **`PortfolioState` removed** — would exactly duplicate the last
  `EquityPoint`; the result's final state is simply that point.

**Why:** Full exits (D-16) make `removedBasis` always equal `costBasis`
exactly, so no division is ever needed (D-14's reserved `MathContext`
goes unused). The post-SELL cash-`>=0` check is a fail-fast backstop for
OQ2 — `IllegalStateException` here signals a `BacktestConfig` that should
have been rejected at configuration time (see D-23).

**Rejected:** pyramiding/weighted-average basis; exposed `averageCost()`;
a `Position`/lots/FIFO abstraction; stored last price or
equity/unrealizedPnl on `Portfolio`; a mutating `markToMarket`; stored
derived values on `EquityPoint`; `PortfolioState`; a `Portfolio`
interface/service layer; silently repairing invalid financial state.

## D-23 OQ2 resolution: reserve one commission in cash at entry for the eventual exit (revises D-7)

**Decision:** OQ2 cannot be solved by validating `BacktestConfig` alone:
for any `commissionPerFill > 0`, a valid `BarSeries` can drive the exit
fill price arbitrarily close to zero, making SELL proceeds smaller than
the commission. **Counterexample:** `initialCapital=1000, commission=5,
slippage=0, CashFraction(1)`; entry near close 99.5 sizes to 10 shares,
BUY costs exactly 1000, leaving cash 0; a later exit open of 0.01 gives
`10×0.01−5 = −4.90`.

**Resolution:** D-7's formulas reserve one `commissionPerFill` in cash at
entry:

- Sizing: `spendable = min(cash×fraction, cash−commission)`;
  `quantity = floor((spendable−commission) / (close_N×(1+slippageRate)))`.
- Affordability: `requiredCash = quantity×fillPrice + commission +
  commission` (entry commission + reserved exit commission).
- The reserve is **never charged** to the BUY — `Portfolio.apply(BUY)`
  still deducts exactly one commission (D-22 unchanged).

**Proof (sketch):** after an accepted BUY, `cash_after >= commission`
(since the BUY required `q×f+c+c <= cash_before`). No fill occurs between
a BUY and its matching SELL (D-16/D-22 flat↔long). At the SELL:
`newCash >= commission + q×open×(1−slippageRate) − commission =
q×open×(1−slippageRate) > 0` (since `slippageRate < 1`, per D-24). So
`Portfolio`'s D-22 negative-cash guard becomes provably unreachable.

**Why (rejected alternatives):** dataset-aware preflight validation
(path-dependent, complex, rejects legitimate runs); a SELL-rejection type
(the position would become permanently unexitable); changing commission
semantics to a percentage/capped fee (invents a different cost model,
D-9); allowing negative cash (equivalent to leverage); aborting the run
mid-execution (a valid dataset+config could then produce no result at
all).

**Consequence:** OQ2 resolved. D-7's original text is unedited, with a
pointer here. D-21's `requiredCash` gains documented meaning, no
structural change.

## D-24 BacktestConfig, Trade, and BacktestResult

**Decision:** `engine.result` adds:

- `BacktestConfig(initialCapital, commissionPerFill, slippageRate,
  startDate, endDate)`: `initialCapital > 0` (stricter than `Portfolio`'s
  `>=0` — a meaningful *request* is narrower than a valid runtime state);
  `commissionPerFill >= 0`; `0 <= slippageRate < 1` (the bound D-23's
  proof depends on); `startDate <= endDate`. Every `BigDecimal`
  canonicalized (`stripTrailingZeros()`, scale floored at 0) — same
  precedent as `CashFraction` (D-19).
- `sealed Trade{entry();quantity();}` — `Open(entry)` (BUY, still held) /
  `Closed(entry, exit)` (BUY+SELL, same quantity, exit strictly after
  entry, exit orderId > entry orderId). `Closed` derives `realizedPnl()`
  (matches `Portfolio.apply`'s own two expressions exactly),
  `totalCommission()`, `totalSlippageCost()` — none stored. No `Trade` ID
  (`entry().orderId()` suffices). `Trade.fromFills(List<Fill>)` parses an
  already-ordered sequence into trades — never sorts/repairs; throws IAE
  on any non-alternating sequence.
- `BacktestResult(symbol, strategy, config, firstEvaluableDate:
  Optional<LocalDate>, equityCurve, fills, rejections)`: keeps
  `symbol`/`strategy`/`config` but never the `BarSeries` (D-15 owns
  dataset provenance). Every list `List.copyOf`'d and validated
  internally consistent (`equityCurve` non-empty, ascending, in-range;
  `fills` ascending, alternating from BUY; `rejections` non-decreasing) —
  this is what guarantees `trades()`/`finalPoint()` (derived, not stored)
  can never throw on a constructed result. No `PortfolioState`-shaped
  field. Ordering: one `EquityPoint` per in-range bar; fills in execution
  order; rejections in append order within a bar (`InsufficientCash`
  before `ZeroQuantity` on the same date). No timestamps —
  `LocalDate` + execution phase suffices.

**Why:** Sealed `Trade` avoids an ambiguous nullable exit field.
`firstEvaluableDate` as `Optional` expresses "may not exist" honestly (the
indicators never became ready in range — a valid historical fact, not an
error).

**Rejected:** a single `Trade` record with nullable exit; a `Trade` ID;
storing `Trade`'s derived figures; storing the `BarSeries` on the result;
a `PortfolioState`-shaped result field; sorting/repairing fills; synthetic
timestamps.

**Consequence:** The full non-Backtester V1 domain model is frozen. The
only remaining implementation gap is `Backtester.run(...)` itself; the
`IndicatorSpec -> Indicator` construction it needs
(`Indicator.create(IndicatorSpec)`, an exhaustive switch with no `default`
branch) is implemented.

## D-25 The V1 Backtester: stateless entry point, run-scoped Run, D-23 sizing as a pure function

**Decision:** `engine.Backtester` is a stateless `public final class`
(no fields). `run(series, strategy, config)` null-checks its arguments
and delegates to a private, per-call `Run` (a `private static final`
nested class) that owns every mutable value — `Portfolio`, a
`LinkedHashMap<IndicatorSpec, Indicator>` (one instance per
`requiredIndicatorSpecs()`, in canonical order), the single pending
`Order`, the sequential order-id counter, `firstEvaluableDate`, and the
three result lists. `Run` is discarded after `execute()`; nothing
mutable escapes.

Per bar, in order: execute a pending order at that bar's open (D-7) →
update every indicator with that bar's close → skip further processing
for a lookback bar → record one `EquityPoint` (after any open-time fill,
marked to this bar's close) → note `firstEvaluableDate` the first time
every required indicator is ready → evaluate exactly one condition
(entry when flat, exit when long) unless indicators aren't ready yet or
this is the last in-range bar (D-8, D-12) → on a true condition, size
(ENTER) or fill-in-full (EXIT) and queue one `Order`. Bars after
`endDate` stop the loop; before any bar is processed, the run rejects a
series with no bar in `[startDate, endDate]`.

ENTER sizing is `Backtester.enterQuantity(cash, fraction, close,
commission, slippageRate)`, a package-private pure static method
implementing D-23's reserve arithmetic exactly
(`spendable = min(cash×fraction, cash−commission)`; whole shares via
`BigDecimal.divideToIntegralValue`, never `MathContext` or a rounding
mode). BUY affordability at execution charges
`quantity×fillPrice + commission + commission` against available cash
(entry commission plus the D-23 reserve) but `Portfolio.apply` still
deducts only one commission — the reserve is never a second BUY fee. A
SELL carries no affordability check, per D-23's proof; `Portfolio`'s own
negative-cash guard remains a backstop, and if it fires, that exception
is allowed to surface, not converted into a rejection.

`ZeroQuantity` (sizing ≤ 0) and `InsufficientCash` (execution
unaffordable) are recorded exactly as D-21 defines them; a `ZeroQuantity`
consumes no order id, and an order that later becomes
`InsufficientCash` keeps the id it was assigned. `Trade` derivation and
`BacktestResult` construction use only the already-frozen D-24 APIs
(`Trade.fromFills`, and the `BacktestResult` constructor's own ordering
validation) — the loop keeps no trade state of its own.

**Why:** A stateless `Backtester` plus one throwaway `Run` per call
keeps D-5's re-entrancy trivially true, with nothing to reason about
across calls. Executing orders inline inside `Run` (rather than a
separate execution service) keeps the whole chronology in one
readable, top-to-bottom method — the smallest structure that stays
verifiable for look-ahead bias. `enterQuantity` as a pure function
(rather than inline in the loop, or a class of its own) is directly
unit-testable against D-23's formula without needing a full run.

**Rejected:** an execution/broker service layer; a mutable `Backtester`;
a static/global order-id counter (would leak state across runs); storing
Trade state during the loop (D-24 already derives it from `fills`);
converting a `Portfolio` invariant violation into a rejection (it must
surface as an engine-bug signal, not be hidden).

**Consequence:** The V1 engine is feature-complete for its scope.
`Backtester.run(...)` is the last piece D-1 through D-24 were building
toward; only performance metrics, backend integration, and frontend
remain, none of which change engine behavior.

## D-26 V1 performance-metric conventions: `PerformanceMetrics.of(BacktestResult)`

**Decision:** `engine.metrics.PerformanceMetrics` is one immutable record
computed by a pure static `of(BacktestResult)`, and participates in none of
signal generation, sizing, execution, portfolio mutation, or chronological
processing (D-5) — post-run analysis of an already-produced result only.

- **Preconditions** (IAE if violated, NPE for a null result): the first
  equity point's equity equals `config.initialCapital()` exactly, and
  every equity point's equity is strictly positive. Both always hold for
  genuine `Backtester` output (no fill can occur before the second
  in-range bar; cash cannot go negative, D-23) — the checks keep the
  formulas below mutually consistent rather than reject real runs.
- **Periodic returns**: simple arithmetic returns between consecutive
  equity points only — `n` points give `n-1` returns. `initialCapital` is
  never a separate observation. A data gap between two consecutive equity
  points remains exactly one return observation; V1 does not calendar-gap
  adjust it — an intentional limitation of the daily-bar model.
- **Total return**: `(finalEquity − initialCapital) / initialCapital`. An
  open final position counts at its final mark-to-market equity (D-8); a
  no-trade run is exactly `0.0`.
- **CAGR**: ACT/365 Fixed over the observed span (first to last equity-
  point date, not the configured range) — `StrictMath.pow(finalEquity /
  initialCapital, 365/days) − 1`. **Empty when that span is under 365
  days** (GIPS convention against annualizing sub-year returns) — this
  covers a same-day range and any run under a year, even one spanning a
  full calendar year with fewer than 365 observed days.
- **Volatility**: sample standard deviation (divisor `n-1`) of the
  periodic returns, annualized by a fixed `× √252` (assumes daily bars).
  Empty for fewer than two returns. If every return is bitwise-equal, the
  standard deviation is defined as exactly `0.0` — without this rule, mean
  subtraction over identical doubles leaves ~1e-17 of spurious dispersion.
- **Sharpe ratio**: `mean(returns) / stdDev × √252`, risk-free rate fixed
  at zero (no config field). Empty for fewer than two returns or zero
  volatility (including a flat no-trade run, where the ratio is
  undefined). Never `NaN`/`Infinity`.
- **Maximum drawdown**: the largest close-to-close fall from a running
  peak equity, as a fraction of that peak (`0.25` = 25%). Percentage only
  — no absolute amount, drawdown series, duration, or peak/trough dates.
  Always present; `0.0` for a single point or monotonically rising equity.
- **Trade statistics**: derived only from `result.trades()`, using
  `Trade.Closed.realizedPnl()` directly, never recomputed. Only closed
  trades count (`closedTradeCount`); an open trade is never a win or loss.
  P&L above zero is a win, below zero a loss, exactly zero is breakeven —
  breakeven counts in the win-rate denominator but excluded from both
  averages. `winRate` empty iff `closedTradeCount == 0`; `averageWin`/
  `averageLoss` empty when there are no wins/losses; `averageLoss` is
  signed negative.
- **Numerical policy**: every metric is a `double` (D-14). Monetary
  differences and sums are performed exactly in `BigDecimal`; conversion
  to `double` happens only at each metric's own statistical calculation
  boundary. `StrictMath.pow`/`StrictMath.sqrt` throughout (including the
  `StrictMath.sqrt(252.0)` annualization factor), never `Math.pow`/
  `Math.sqrt`, for bit-reproducible results across platforms (D-15). No
  `MathContext`, no rounding to cents.

**Why:** A single flat record (not sub-records) is the smallest shape for
nine fields that are all "facts about one run." `OptionalDouble` with one
documented emptiness condition per metric avoids `NaN`/`Infinity`/sentinel
ambiguity without a nullable-`Double` or a giant Optional-of-everything
type. Fixed 252/zero-risk-free/ACT-365 conventions are stated once here so
the implementation never has to invent them.

**Rejected:** a synthetic time-zero equity point or `initialCapital` as a
return observation (redundant — the first equity point already equals it
for genuine `Backtester` output — and would bias volatility/Sharpe); log
returns; population standard deviation; calendar-day or bar-count-derived
annualization; `Math.pow`/`Math.sqrt` (breaks platform determinism); a
risk-free-rate config field; reporting Sharpe as `0`/`Infinity` at zero
volatility; annualizing CAGR under 365 days; `BigDecimal` +
`MathContext.DECIMAL64` for the averages; a monetary drawdown or drawdown
series; counting open trades; treating breakeven as a win or excluding it
from the win-rate denominator; a metrics interface/registry or
sub-records; adding these preconditions to `BacktestResult` itself (would
change a frozen D-24 type for a metrics-only need).

**Consequence:** trading-cost totals (commission/slippage sums — easily
derivable from `fills()`), a buy-and-hold benchmark (the result keeps
closes but no opens — needs its own checkpoint), a non-zero risk-free
rate, Sortino/Calmar/beta/alpha/VaR, and drawdown duration/dates are all
explicitly deferred, not part of this decision.

## D-27 Trading-cost totals on BacktestResult

**Decision:** `BacktestResult.totalCommission()` and
`totalSlippageCost()` — derived methods, not components or stored fields,
following the `trades()`/`finalPoint()` precedent (D-24) — are `Σ
fill.commission()` and `Σ fill.slippageCost()` over **all** fills, BUY and
SELL, including a final open trade's entry fill; never a hypothetical exit
cost for a still-open position (D-8). `BigDecimal.ZERO` with no fills;
exact `BigDecimal` addition only, no `MathContext`, no rounding, no
conversion to `double`. No change to the `Backtester` loop, `Portfolio`,
`Fill`, `Trade`, `EquityPoint`, D-7/D-23 sizing, or D-26.

Commission is actual cash paid:
`finalCash = initialCapital − Σ_BUY(q×fillPrice) + Σ_SELL(q×fillPrice) −
totalCommission`. Slippage cost is the implicit adverse-fill cost relative
to each bar's open, already embedded in fill prices — **not** an
additional cash flow, and never combined with commission into one total.

**Why:** these are exact ledger aggregations of raw fill data, the same
kind of structural derivation as `trades()`/`finalPoint()` — not `double`
statistics, so they don't belong on `PerformanceMetrics` (D-26 fixes every
metric there as a `double`). Summing over `Fill` rather than `Trade.Closed`
is required so an open trade's entry costs are not missed.

**Rejected:** cost fields (`BigDecimal` or `double`) on
`PerformanceMetrics`; a `TradingCosts` record (two sums don't justify a
type); a combined commission-plus-slippage total (implies a cash figure
that doesn't exist); leaving cost aggregation to the backend (the
all-fills scope, including a trailing open entry, would be reinvented
there).

**Consequence:** the buy-and-hold benchmark (open question 5) remains
unresolved — a design review of an initial `BuyAndHold` implementation
found it economically unsound (with `CashFraction(1)`, a benchmark's entry
can be delayed, or on a steadily rising series prevented for the whole
run, by the same gap-up rejection behaviour a user strategy has under D-7)
and it was withdrawn before commit. The benchmark is deferred to its own
checkpoint, D-28.

## D-28 Buy-and-hold benchmark as an independent post-run calculation

**Decision:** `engine.metrics.BuyAndHoldBenchmark(BigDecimal
initialCapital, List<EquityPoint> equityCurve)` answers "what would the
same starting capital have produced by passively holding the asset over
the requested backtest period?" — computed directly from `BarSeries` and
`BacktestResult`, **never** by calling `Backtester.run`, evaluating a
`StrategyDefinition`, or using `Portfolio`, `Order`, `Fill`, or the D-23
strategy sizing/reserve machinery (this corrects the withdrawn D-27
`BuyAndHold`, which delegated to `Backtester` and so inherited the
strategy's gap-up rejection behaviour).

- **Entry (option A — the first in-range bar's open):** the benchmark
  buys once, at `fillPrice = firstInRangeBar.open × (1 + slippageRate)`,
  never sells, and is never rejected — it is sized from the price actually
  paid, so it is always affordable. Whole shares:
  `q = floor((initialCapital − commission) / fillPrice)` via
  `divideToIntegralValue`/`longValueExact` (exact, no `MathContext`). If
  `initialCapital − commission ≤ 0` or `q` would be `0`, no trade occurs
  and no commission is charged. If `q > 0`, one commission is paid and
  `costBasis = q×fillPrice + commission`.
- **Why option A, not the strategy's N-close/N+1-open convention (option
  B) or a market-on-close entry (option C):** a *passive* holder has no
  decision to make and so no decision delay — importing the strategy's
  timing would handicap the benchmark by the same defect just removed. A
  benchmark and a strategy both start with exactly `initialCapital` at the
  first in-range bar's open (a strategy holds no position and has no
  pending order before that point, D-6) and both end at the same last
  in-range close, so the two are compared over the same window and same
  capital. A one-bar window still produces a real result under A; it never
  invests under B.
- **No exit ever:** no exit commission, no exit slippage, no D-23 reserve,
  and residual cash earns nothing (V1 has no interest model). Slippage is
  represented only through the changed fill price, never as a separate
  cash flow (consistent with D-27).
- **Equity curve:** one `EquityPoint` per in-range bar, with dates
  identical to `result.equityCurve()`'s. Because entry happens before the
  first mark, every point shares the same `cash`, `quantity` and
  `costBasis`, with `realizedPnl = 0` — only `date` and `close` vary. The
  first point is marked at the first in-range bar's close, so its equity
  generally differs from `initialCapital` (unlike a strategy's first
  point) — this is why `initialCapital` is carried as its own record
  component rather than read off the curve. `EquityPoint` itself is
  reused unmodified: each point is exactly what `Portfolio` would produce
  after the same BUY, marked to that close.
- **Input contract, `of(series, result)`:** only `result.symbol()`,
  `config()` and `equityCurve()` are read — never `strategy()`, `fills()`,
  `rejections()`, `trades()`, or `firstEvaluableDate()` — so two different
  strategies over the same series and config give an identical benchmark.
  The caller-supplied "same data" contract is enforced, not assumed:
  `series.symbol()` must equal `result.symbol()`, and the series' in-range
  bars (by `result.config()`'s date range) must match
  `result.equityCurve()` exactly in count, date and close (by
  `compareTo`), each an `IllegalArgumentException` on mismatch. This
  cannot detect a series differing only in its bar opens or lookback
  content — a documented limit, not a gap the check could cheaply close.
- **Record validation** makes an invalid benchmark unrepresentable:
  `initialCapital > 0`; a non-empty, strictly-ascending, defensively
  copied curve; every point sharing the same cash/quantity/costBasis;
  every `realizedPnl == 0`; and `cash + costBasis == initialCapital`
  exactly at every point (the no-realized-P&L accounting identity). The
  first point's equity is **not** required to equal `initialCapital`
  (unlike D-26's `PerformanceMetrics` precondition) — under option A it
  generally doesn't.
- **Package:** `engine.metrics`, alongside `PerformanceMetrics` — this is
  post-run analysis of a finished result plus market data, with no
  simulation in it (D-5). It depends on `data`, `portfolio` and `result`,
  never on `Backtester`, `strategy`, or `execution`.

**Why:** a benchmark and a strategy carrying the same frictions (whole
shares, commission, slippage, idle-cash drag) isolates timing and
selection from cost effects, and whole/fractional-share economics stay
consistent with V1's "whole-share quantities" scope (CLAUDE.md). Reading
only `symbol`/`config`/`equityCurve` off the result, never `strategy()` or
`fills()`, is what makes independence from `StrategyDefinition` a checked
fact rather than a convention.

**Rejected:** entering at the second in-range bar's open (option B —
carries the strategy's decision delay into a passive policy, and a
one-bar window never invests); entering at the first in-range bar's close
(option C — captures the bar-0-close-to-bar-1-open gap a strategy can't,
and needs no `BarSeries`, but conflicts with "passive ownership of the
requested window" starting at its open); delegating to
`Backtester.run`/`StrategyDefinition` (the withdrawn design — reintroduces
gap-up rejection); fractional shares or a frictionless price return
(inconsistent with V1's whole-share, cost-bearing engine, and would
flatter the benchmark relative to anything the engine can actually
produce); a combined commission-plus-slippage cash flow (D-27); requiring
`equityCurve[0].equity == initialCapital` (true for a strategy under
D-26, not for this benchmark under option A); benchmark CAGR, volatility,
Sharpe or drawdown (`PerformanceMetrics` is fixed to `BacktestResult` by
D-26; extending it to a bare equity curve is a separate decision);
excess-return/comparison fields, interfaces, registries, or multiple
benchmark types.

**Consequence:** open question 5 is resolved. Excess return (strategy
`totalReturn()` minus benchmark `totalReturn()`) and chart pairing are
consumer concerns, computed by the backend/frontend, not part of this
decision. Price basis (raw vs. adjusted) remains open question 1;
dividends and corporate actions are not modeled.

## D-29 Backend foundation: dependency direction, persistence stack, and package root

**Decision:** the first Phase 7 backend batch. `backend` depends on
`engine` as an ordinary Maven module dependency
(`in.vedchangani:engine:0.0.1-SNAPSHOT`) — the dependency direction
remains `backend → engine`, never the reverse, matching D-1. No engine
Java source or the engine `pom.xml` is touched.

Persistence stack, chosen now so later batches build on one foundation:
Spring Data JPA, the PostgreSQL JDBC driver (`runtime` scope), and Flyway
(`flyway-core` + `flyway-database-postgresql`, plus the separate
`spring-boot-flyway` autoconfiguration module — Spring Boot 4.x split
Flyway's autoconfiguration out of `spring-boot-autoconfigure`, unlike
Boot 3.x). `spring.jpa.hibernate.ddl-auto=validate`: schema changes are
owned by Flyway migrations, never by Hibernate. `spring.jpa.open-in-view
=false`. The datasource is environment-backed
(`PARALLAX_DB_URL`/`_USERNAME`/`_PASSWORD`) with local defaults, so a
developer machine needs no extra configuration to run the application.
No `spring.flyway.*` datasource properties are set — Flyway always
migrates the application's own `DataSource` bean, never a separate
connection.

Integration tests use a real **Testcontainers PostgreSQL** (`postgres:
16-alpine`), wired in through Spring Boot's `@ServiceConnection`
mechanism, which registers a `JdbcConnectionDetails` bean that Boot's
datasource autoconfiguration prefers over `spring.datasource.*` — so a
developer's local `PARALLAX_DB_*` environment is never read by tests, and
no second/parallel test-configuration system exists. This is proven, not
assumed: an integration test compares the live `DataSource`'s connection
URL against the container's own `getJdbcUrl()`, and confirms
`flyway_schema_history` exists after context startup. This requires
Docker on the development machine; no other infrastructure (Docker
Compose, application containers) is added.

The backend root package is renamed `in.vedchangani.backend` →
`in.vedchangani.parallax.backend`, matching the engine's
`in.vedchangani.parallax.engine` root. A stateless `Backtester` is
exposed as a single `@Bean` from `EngineConfiguration` — the only point
of contact between Spring configuration and the engine in this batch.

**Why:** `ddl-auto=validate` (never `update`) keeps schema evolution
explicit and reviewable as migrations, consistent with CLAUDE.md's
"never silently invent" principle applied to persistence. A real
PostgreSQL in tests (not H2) means constraint, numeric-scale and
transaction behavior in later batches is actually verified, not merely
approximated.

**Rejected:** H2 or a local PostgreSQL instance for tests (§ design
review — exact behavior over convenience); `ddl-auto=update` (schema
drift with no reviewable history); reusing the engine's package root for
the backend (they are different modules with different concerns);
building service/entity/REST scaffolding in this batch (no persistence
model exists yet to build it against — see the Phase 7 batch sequence).

**Consequence:** no entities, repositories, migrations, REST endpoints,
DTOs, or security exist yet — this batch is foundation only. Every later
Phase 7 batch builds on this datasource/Flyway/Testcontainers setup
without revisiting it.

## D-30 StrategyDefinition DTO mapping and canonical JSON codec

**Decision:** a backend-only, engine-unmodified boundary
(`backend.strategy.definition`) between JSON and the engine's
`StrategyDefinition`: a sealed DTO tree, a path-tracked mapper, and a
deterministic canonical-JSON/SHA-256 codec. No persistence, no REST.
Resolves open question 2.

- **DTO tree:** sealed interfaces mirroring the engine's shape
  (`ConditionDto{Compare,All,Any}`, `OperandDto{Indicator,Close,Constant}`,
  `PositionSizingDto{CashFraction}`), each discriminated by a `"type"`
  property (`compare`/`all`/`any`/`indicator`/`close`/`constant`/
  `cashFraction`) via `@JsonTypeInfo`/`@JsonSubTypes`. Backend-owned
  `IndicatorTypeDto`/`OperatorDto` enums, never the engine's, so the
  stored/transport format never depends on an engine enum's identifiers.
  `StrategyDefinitionDto` (the transport root) carries no
  `schemaVersion` — only `StoredStrategyDocument` (internal, decode-only)
  does.
- **`Constant.value` and `CashFraction.fraction` are JSON strings
  everywhere** (request, response, storage) — never JSON numbers, enforced
  by explicit Jackson coercion config, not merely convention. `Constant`
  round-trips via `Double.toString`/`Double.parseDouble` exactly (Java 19+
  guarantees this bit-exactly for every finite value); `CashFraction`
  parses straight into `BigDecimal`, never through a `double`. A JSON
  string survives PostgreSQL `jsonb` untouched, where a JSON number would
  be rewritten into `numeric` notation (e.g. `1.0E-300` → a 300-digit
  literal).
- **Mapper-owned rules are exactly two**, both syntax, not semantics: the
  decimal grammar `-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?` for both
  string fields, and rejecting a nonzero literal that underflows to `0.0`
  as a `double` (e.g. `"1e-400"`) — both `MalformedStrategyDefinitionException`
  and `InvalidStrategyDefinitionException` respectively, per the table
  below. Overflow (`"1e400"` → `Infinity`) is deliberately *not* caught by
  the mapper; it is let through so the engine's own finiteness check
  rejects it. Every other rule (finiteness, the `-0.0` fold, non-empty
  condition groups, indicator period/RSI bounds, `CashFraction` bounds and
  `stripTrailingZeros` canonicalization) is left entirely to the engine
  constructors — the mapper catches only their `IllegalArgumentException`
  and rewraps it as `InvalidStrategyDefinitionException(path, message)`,
  with a depth-first field path such as
  `entryCondition.conditions[1].right`. An engine `NullPointerException`
  is never caught (a strictly-parsed DTO tree cannot produce one; if it
  does, that is a backend bug and propagates unchanged).
- **Canonical JSON** is never produced by Jackson serialization — a
  hand-written recursive emitter (exhaustive `switch`, no `default`) over
  `mapper.toDto(validatedEngineObject)` writes it, so it can never depend
  on Jackson's field ordering and is always derived from an
  already-validated engine object, never a raw client request. Grammar:
  fixed property order (`type` first; the document starts with
  `schemaVersion`), no whitespace, no trailing newline, engine list order
  preserved exactly, no nulls, JSON numbers only for `schemaVersion`/
  `period`. `schemaVersion` (`= 1`) is embedded as the document's own
  leading property — inside the hashed bytes — so a stored `definition_
  schema_version` column can never be tampered with independently of the
  hash.
- **Hash:** `lowercase-hex(SHA-256(canonicalJson.getBytes(UTF_8)))`,
  computed only over the codec's own canonical text — never over a raw
  client request and never over PostgreSQL's `jsonb` rendering. Loading
  stored data always re-parses, re-maps to the engine, re-encodes
  canonically, and re-hashes before comparing.
- **Decode/integrity** (`decode(schemaVersion, documentJson,
  expectedSha256)`): unsupported schema version → fail; malformed stored
  JSON → fail; document's own `schemaVersion` disagreeing with the
  supplied one → fail; semantically invalid stored tree → fail; malformed
  expected-hash string → fail; recomputed hash ≠ expected → fail. Every
  failure is `StrategyDefinitionIntegrityException` — a 500-class,
  never-client-facing type distinct from the two client-facing exceptions
  (`Malformed…`/`Invalid…`, both path-carrying). Because the comparison is
  against the *re-encoded* hash rather than raw byte equality with `
  documentJson`, a `jsonb`-reformatted (reordered/whitespaced) stored
  document still verifies correctly.
- **Unknown-field policy:** rejected everywhere — unknown/duplicate/
  missing/null properties, null list elements, trailing tokens, unknown or
  missing type ids, and every scalar coercion (number/boolean → string,
  string/float → int, case-insensitive enums). One private, explicitly
  configured Jackson `JsonMapper` inside the codec enforces this — never
  Spring's global (lenient) mapper. Reproducibility outweighs forward
  compatibility in V1: a stored document must never silently carry
  information the engine doesn't understand, and a client typo must never
  be silently dropped.

**Why:** the DTO tree stays intentionally close to the engine's shape so
the mapper is a straightforward structural walk rather than a translation
layer, while numeric fields diverge from the engine's native types
(`double`/`BigDecimal`) specifically where a JSON number would lose
exactness. Input canonicalization happens from the validated *engine*
object, never from raw request bytes, so two requests describing the same
definition with different formatting (whitespace, key order, `"0.500"` vs
`"5E-1"`) always produce identical canonical text and hash — this is what
makes `definition_hash` a property of the definition, not of how a client
happened to write it.

**Rejected:** JSON numbers for `Constant`/`CashFraction` (loses exactness
through `jsonb` and through lenient JSON libraries); accepting both JSON
numbers and strings for those fields (two input paths per field, no real
benefit); silently rounding constant underflow to `0.0` (would let a
nonzero threshold silently become always-true/false); duplicating engine
semantic rules as Bean Validation annotations on the DTO tree; a separate
hash-utility class (SHA-256 stays a private codec method until a second
genuine use case appears — D-31's dataset content hash — justifies
sharing it); accepting unknown JSON properties for forward compatibility
(reproducibility wins in V1); hashing raw request JSON or a database's
`jsonb` text representation directly.

**Consequence:** open question 2 is resolved. The codec's output
(canonical text + hash + schema version) is designed to be stored
directly as-is by a later persistence batch (D-31): `jsonb` for the text,
a `char(64)` for the hash, an `int` for the schema version — no format
change anticipated at that boundary.

## D-31 Strategy persistence, ownership seam, and strategy REST

**Decision:** the first persistent, user-owned resources:
`AppUser → Strategy → immutable StrategyVersion`, plus the owner-scoped
strategy REST API (`/api/strategies`). Packages: `user`, `strategy`,
`api`, alongside the unmodified D-30 `strategy.definition`.

- **Schema** (`V1__create_user_and_strategy.sql`): `app_user` (username
  unique); `strategy` (`owner_id` FK `ON DELETE RESTRICT`, `name`/
  `description`, `latest_version_number`, unique on `(owner_id, name)`);
  `strategy_version` (`strategy_id` FK `ON DELETE RESTRICT`,
  `version_number`, `definition jsonb`, `definition_schema_version`,
  `definition_hash`, unique on `(strategy_id, version_number)`). No
  `owner_id` on `strategy_version` — ownership is always resolved through
  a join to `strategy`. A CHECK ties `definition_schema_version` to the
  document's own embedded `schemaVersion`; another CHECK enforces a
  lowercase-hex 64-character hash. `definition_hash` is **`varchar(64)`**,
  not the `char(64)` D-30 anticipated — `bpchar` pads, and the CHECK
  already enforces the exact length, so a plain `varchar` plus regex is
  the cleaner Hibernate `validate` mapping. `V2__seed_development_user.sql`
  inserts the deterministic `dev` user.
- **Immutability** (`strategy_version`: no update, no delete) is enforced
  at four layers: database `BEFORE UPDATE OR DELETE`/`BEFORE TRUNCATE`
  triggers; Hibernate `@Immutable` plus `updatable=false` on every column;
  no entity setters; no repository or REST update/delete method.
- **Entities** use plain `long` foreign-key columns, never JPA
  associations/collections (safe under `open-in-view=false`, since there
  is no lazy graph to fetch outside a transaction). Construction/mutators
  are package-private; `StrategyService` is the only write path.
  `StrategyVersion` is built only from D-30 `CanonicalStrategyDefinition`
  output, stored via `@ColumnTransformer(write = "?::jsonb")` — never a
  second serializer. Reads always go through D-30 `decode(...)`, which
  re-parses/re-maps/re-encodes/re-hashes rather than trusting PostgreSQL's
  own `jsonb` rendering byte-for-byte.
- **Version allocation:** `codec.encode` runs before any transaction
  opens; the transaction takes an owner-scoped `SELECT ... FOR UPDATE` on
  `Strategy` (READ COMMITTED — the PostgreSQL default, never raised),
  allocates `latest + 1`, updates the parent, and inserts the version.
  `uq_strategy_version_number` is the backstop; a failed attempt rolls
  back the whole transaction (parent counter included), so no number is
  consumed. Creating a new `Strategy` plus version 1 needs no lock (the
  row is invisible to others until commit) — `uq_strategy_owner_name`
  alone decides a concurrent duplicate name. **Every write to a
  `Strategy` row, including a metadata PATCH, takes the same lock** — an
  unlocked PATCH could otherwise race a concurrent `createVersion` and
  silently revert `latest_version_number`, since Hibernate's default
  UPDATE writes every column.
- **Ownership:** every repository method `StrategyService` uses is
  owner-scoped (`findByIdAndOwnerId`, `lockByIdAndOwnerId`,
  `findOwned`/`findAllOwned` joining through `Strategy`) — no
  `findAll`/unrestricted `findById`. A nonexistent resource and another
  owner's resource are indistinguishable, both 404. `CurrentUser`
  (`SeededCurrentUser` today, resolving the seeded `dev` user on every
  call) is the sole source of the owner id — no request ever supplies
  one, and a later Spring Security-backed `CurrentUser` replaces only
  that bean.
- **REST + D-30 boundary:** every strategy-definition-bearing body is
  read with `@RequestBody String` and parsed exactly once by the D-30
  strict codec, never Spring's global JSON binding. Two additive D-30
  overloads make this possible, both delegated to by the original method
  with unchanged behavior: `StrategyDefinitionCodec.parseRequest(String,
  Class<T extends Record>)` and `StrategyDefinitionMapper.toEngine(
  StrategyDefinitionDto, String rootPath)` (prefixes every
  semantic-validation path, e.g. `definition.entryCondition.left`, for a
  definition nested inside a request envelope). **These two overloads are
  the only D-30 source changes in this decision** — canonical JSON,
  hashing, strictness configuration, the DTO hierarchy, and every existing
  D-30 test are unchanged. Envelope Bean Validation (`@NotBlank`/`@Size`
  on `name`/`description`) is invoked explicitly against the parsed
  record, since `@Valid` cannot apply to a raw `String` parameter.
  Responses carry the D-30 transport DTO shape for a definition (never
  canonical text, never `schemaVersion`), so a `GET .../versions/{n}` body
  posts back unchanged and produces the same `definitionHash`.
- **Errors** (`ProblemDetail`): malformed body/envelope validation → 400;
  semantically invalid definition → 422; not-found/cross-owner → 404
  (identical either way); duplicate name/version conflict → 409 (matched
  against the actual database constraint name, not a racy `exists()`
  pre-check); stored-data integrity failure or any other unexpected
  error → 500, logged, generic body — never an exception class name,
  stack trace, SQL, or constraint name.

**Why:** D-30 remains the sole authority for strategy-definition
semantics, syntax, canonicalization, and hashing — D-31 only adds the two
narrow overloads needed to run that same strict parser against a request
envelope rather than a bare document. The pessimistic lock (not optimistic
versioning) makes version-number allocation trivially reason-about: one
row lock serializes every writer, so 1..n-with-no-gaps is enforced by
construction rather than by retry logic.

**Rejected:** `char(64)` for the hash column (D-30's original suggestion —
padding and `validate` friction outweigh the benefit over `varchar` plus a
CHECK); JPA associations/collections between `Strategy` and
`StrategyVersion` (an unnecessary entity graph under
`open-in-view=false`); an `owner_id` column on `strategy_version`
(ownership is already inherited through `Strategy`); binding the request
envelope with Spring's lenient global mapper and re-serializing the
nested definition into the strict codec (a second, competing JSON reader,
and the lenient read would already have silently accepted duplicate keys
or an unknown `ownerId` field); a racy `exists()` pre-check for duplicate
names/versions (the database constraint is authoritative); deleting the
seeded `dev` user once it owns strategies (its future lifecycle is
disabling/replacing it, never deleting — `ON DELETE RESTRICT` already
prevents this); `password_hash` in this batch (added by the later
authentication migration); PATCH as a partial update (full metadata
replacement only — `name` and `description` both required); deduplicating
identical-content `StrategyVersion`s (versions record events, not unique
contents); pagination, DELETE/PUT endpoints, Spring Security, datasets,
and backtests (out of scope for this batch).

**Consequence:** the ownership and immutable-versioning model every later
dataset and backtest run will build on is now in place. A run will
reference a `StrategyVersion`, never a `Strategy`.

## D-32 Dataset persistence, CSV ingestion, and content-hash integrity

**Decision:** the first persistent market-data layer: `AppUser → Dataset →
immutable DatasetVersion → immutable dataset_bar rows`, plus a V1 CSV
upload REST API (`/api/datasets`). Packages: `dataset`, `dataset.csv`,
alongside the existing `api`. No Alpha Vantage, no `MarketDataProvider`
interface, no `BacktestRun` — this batch establishes the exact dataset
snapshot a future run will reference.

- **Schema** (`V3__create_dataset.sql`): `dataset` (`owner_id` FK `ON
  DELETE RESTRICT`, `name`/`symbol` both fixed at creation — no PATCH in
  this batch, `latest_version_number` starting at **0** since a dataset
  may exist with no versions yet; unique on `(owner_id, name)` and on
  `(id, symbol)`); `dataset_version` (immutable snapshot: `symbol` a
  copy of the parent's, tied to it by a **composite FK** `(dataset_id,
  symbol) -> dataset(id, symbol)` so a version's symbol can never
  disagree with its dataset's; `source`, `source_detail`,
  `adjustment_basis`, `bar_count`, `first_date`, `last_date`,
  `content_hash`; unique on `(dataset_id, version_number)`); `dataset_bar`
  (no surrogate id — PK `(dataset_version_id, bar_date)`; unconstrained
  `numeric` OHLC columns, preserving whatever scale is inserted).
  Immutability on `dataset_version`/`dataset_bar` is enforced the same
  way as D-31's `strategy_version`: `BEFORE UPDATE OR DELETE`/`BEFORE
  TRUNCATE` triggers, plus (for `dataset_version`) Hibernate `@Immutable`
  and `updatable=false`. No OHLC semantic CHECK is added in SQL — `Bar`
  remains the sole semantic authority (CLAUDE.md); the database only
  enforces syntax-level constraints (hash format, symbol grammar,
  non-blank name, known enum values, `bar_count >= 1`, `first_date <=
  last_date`).
- **Symbol grammar:** `^[A-Z0-9][A-Z0-9._-]{0,31}$` (1–32 chars,
  uppercase ASCII letters/digits plus `.`/`_`/`-`, alphanumeric start).
  Lowercase is rejected outright, never uppercased — comparison is exact
  and case-sensitive. Enforced identically as Bean Validation on the
  create request and as `ck_dataset_symbol_format`.
- **CSV contract** (`dataset.csv.CsvBarParser`, plain Java, no CSV
  library — the fixed, unquoted, 6-field-comma-separated shape never
  needs one): an optional single leading UTF-8 BOM is stripped; every
  remaining byte must be 7-bit ASCII; LF/CRLF/mixed line endings are all
  accepted with the final terminator optional; the header must be
  exactly `date,open,high,low,close,volume`; every data row must have
  exactly six unquoted, unwhitespaced fields. Dates/prices/volume are
  parsed under strict regex grammars — never through `double` — then
  `new Bar(...)` is the sole semantic authority; its
  `IllegalArgumentException` is caught and rewrapped as
  `InvalidCsvDataException(line, message)`, never duplicated. The one
  deliberate exception is date ordering: the parser itself tracks the
  previous row's date so a duplicate/out-of-order date can be reported
  with its exact line — `BarSeries` still re-validates the complete
  sequence as the final authority. `MalformedCsvException` (syntax) maps
  to 400; `InvalidCsvDataException` (semantics/ordering/empty-dataset) to
  422.
- **Price canonicalization:** `stripTrailingZeros()`, then `setScale(0)`
  if scale is negative — the same rule as `BacktestConfig`/`CashFraction`,
  copied into a small backend-local `DatasetContent.canonicalPrice`
  method rather than exposed from the engine. Applied to O/H/L/C only,
  before `Bar` construction, so the validated `BarSeries`, the stored
  `dataset_bar` rows, and the content hash all agree on scale (`100`,
  `100.0`, `100.00` all become one value).
- **Content hash** (`DatasetContent`): SHA-256, lowercase hex, over the
  payload `PARALLAX-BARS/1\n<symbol>\n<date>,<open>,<high>,<low>,<close>,
  <volume>\n...` (UTF-8, every line including the last ending in one LF;
  dates via `toString()`, prices via canonical `toPlainString()`, volume
  via `Long.toString`). Computed from the **normalized `BarSeries`**,
  never raw CSV bytes — different byte-level formatting that parses to
  the same series produces the same hash. `DatasetContent.of` rejects a
  non-canonical price rather than silently normalizing it. SHA-256 stays
  a private `DatasetContent` method rather than a shared helper with
  D-30's codec: the two hash entirely different payload shapes, and
  duplicating six lines is cheaper than a shared package for that (this
  closes D-30's "Rejected" note anticipating this batch as the second use
  case).
- **Transaction/version allocation:** identical shape to D-31 — CSV
  parsing, `BarSeries` construction, canonicalization, and hashing all
  happen **before** any transaction opens; a short transaction then takes
  an owner-scoped `SELECT ... FOR UPDATE` on `Dataset`, allocates `latest
  + 1`, updates the parent, inserts the `DatasetVersion`, and batch-inserts
  every `dataset_bar` row (JDBC batch size 1000) — all on one connection,
  no network/file I/O while the lock is held. A failed attempt rolls back
  the counter, the version row, and every bar row; `uq_dataset_version_number`
  is the backstop, mapped to `DatasetVersionConflictException` (409).
- **`dataset_bar` is plain JDBC, not a JPA entity** (a deliberate
  departure from D-31's all-Spring-Data style): an assigned composite key
  with no surrogate id would make Spring Data's `save` issue a `merge`
  (one `SELECT` per row), and thousands of managed entities per read add
  nothing for rows that are never individually edited. A package-private
  `DatasetBarRepository` (`JdbcTemplate`) exposes only a batch
  `insertAll` and an owner-scoped, date-ordered `findOwned` — no
  update/delete method exists, matching the table's own immutability
  triggers. It is the only place a `BarSeries` can be assembled outside
  this decision's own verification path.
- **Ownership:** identical shape to D-31 §10 — every repository method
  owner-scoped (`findByIdAndOwnerId`/`lockByIdAndOwnerId` on `Dataset`;
  `findOwned`/`findAllOwned` on `DatasetVersion` joining through
  `Dataset`; `DatasetBarRepository.findOwned` joining through both).
  Missing and cross-owner resources are indistinguishable, both 404.
- **Integrity verification** (`DatasetService.getVerifiedSeries` — the
  **only** way a `BarSeries` leaves the `dataset` package): reconstructs
  the series from stored bars, then `DatasetContent.verify` recomputes
  the hash/count/first/last date and compares against the stored values
  exactly. Any mismatch — including a non-canonical stored price, caught
  the same way `DatasetContent.of` catches it — is a
  `DatasetIntegrityException` (500, logged, generic body). Nothing is
  ever repaired or resaved. A metadata-only `getVersion` never reads bars
  and therefore never verifies.
- **Adjustment basis:** required on every version, no default (silently
  defaulting to `RAW` would risk mislabeling adjusted data); an
  unverified uploader declaration, excluded from the content hash, and
  ignored by the engine. D-32 performs no adjustment calculation of any
  kind.
- **REST:** `POST`/`GET`/`GET {id}` for datasets;
  `POST`/`GET`/`GET {version}`/`GET {version}/bars` under
  `.../{id}/versions` — no PATCH/PUT/DELETE, no pagination. The dataset
  create body is read by the same strict D-30 codec reader used for every
  other request envelope (`StrategyDefinitionCodec.parseRequest`, no
  strategy-definition content involved) — never Spring's lenient global
  binding. Version creation reads a raw `MultipartHttpServletRequest`
  directly (not `@RequestParam` binding) to enforce the exact multipart
  shape: exactly one `file` part, exactly one `adjustmentBasis` value
  (case-sensitive, no default), nothing else. Bars are returned
  unpaginated, with prices as JSON strings (the D-30 precedent) and
  volume as a JSON number.

**Why:** market data must be abstracted behind a provider interface
eventually (CLAUDE.md), but CSV upload is the only source D-32 has —
introducing `MarketDataProvider` now, with one implementation, would be
exactly the kind of speculative abstraction CLAUDE.md warns against; it
is added when Alpha Vantage (a second, genuinely different source)
arrives. The composite FK (rather than relying on the hash alone to catch
symbol drift) makes an inconsistent version unrepresentable at the
database level, not merely detectable after the fact.

**Rejected:** a `MarketDataProvider` interface before a second source
exists; a shared SHA-256 helper package (D-30's anticipated one — the two
payload shapes never overlap); OHLC semantic CHECK constraints in SQL
(would duplicate `Bar`'s authority); a JPA entity for `dataset_bar`; a
Dataset metadata PATCH in this batch; defaulting `adjustmentBasis` to
`RAW`; a CSV library for a fixed, unquoted 6-field format; sorting or
repairing out-of-order/duplicate CSV rows; trusting a stored `content_hash`
without recomputing it; pagination on the bars endpoint; dataset
sharing/public datasets.

**Consequence:** the exact historical dataset snapshot a future
`BacktestRun` will reference now exists and is independently verifiable.
Alpha Vantage integration, backtest orchestration, and result persistence
remain future checkpoints.

## D-33 Alpha Vantage `MarketDataProvider`: contracts, parser, HTTP adapter, and dataset persistence integration

**Decision:** the first genuinely second market-data source. Packages
`marketdata` (`MarketDataProvider`, `DailyBars`, `HistoryDepth`, the
`MarketDataException` hierarchy — no Spring dependency) and
`marketdata.alphavantage` (`AlphaVantageDailyParser`, plain Java;
`AlphaVantageMarketDataProvider`, JDK `HttpClient`; `AlphaVantageProperties`,
an `@ConfigurationProperties` record; `AlphaVantageConfiguration`, the
minimal Spring wiring). Delivered in four batches: contracts/parser, the
HTTP adapter/configuration, integrating the provider into D-32's existing
`Dataset`/`DatasetVersion` persistence pipeline (Batch 3), then the REST
endpoint and error-boundary mapping (Batch 4, below) — the ingestion path
is now end to end.

- **Exception hierarchy:** `MarketDataException` (abstract) with four
  concrete subclasses distinguishing *why* a fetch failed —
  `MarketDataRequestRejectedException` (provider rejected the request
  itself — an `"Error Message"` response), `MarketDataCapabilityException`
  (a standing account/plan limitation — `FULL` history requested against a
  compact-only account), `MarketDataUnavailableException` (temporary —
  rate-limit/`"Note"`/`"Information"` control responses, HTTP 429, or a
  blank API key), `MarketDataResponseException` (the fallback — malformed
  JSON, an unrecognized shape, any other non-2xx status, or a transport
  failure), and `InvalidMarketDataException` (the response parsed but the
  market data itself is semantically invalid). No subclass, and no thrown
  exception message anywhere in the hierarchy, ever carries a provider raw
  response body, a request URI, or the API key.
- **Parser strictness** (`AlphaVantageDailyParser`): control-response
  classification runs before success-shape validation; top-level, `Meta
  Data`, and per-bar shapes are all checked against the exact documented
  Alpha Vantage fields (no extra/missing property, correct JSON type),
  under strict date/price/volume regex grammars — never through `double`.
  `Bar` remains the sole semantic authority (CLAUDE.md); its
  `IllegalArgumentException` is caught and rewrapped as
  `InvalidMarketDataException`, never duplicated. A non-descending
  (including duplicate) provider date sequence is a market-data problem,
  not a shape problem, so it is also `InvalidMarketDataException`. Valid
  input is reversed once into the ascending order `DailyBars` exposes.
- **HTTP adapter** (`AlphaVantageMarketDataProvider`): one blocking JDK
  `HttpClient` GET per call — no retries, no throttling, no async behavior.
  Redirects are never followed (`HttpClient.Redirect.NEVER`); a redirect is
  not a documented Alpha Vantage success path, and following one silently
  would risk leaking the API key to an unintended host. The query
  (`function`, `symbol`, `outputsize`, `datatype=json`, `apikey`) is
  assembled by concatenating already `URLEncoder`-encoded values onto
  `baseUrl`, then parsed once via the single-string `URI` constructor —
  **not** the multi-argument `URI(scheme, authority, path, query,
  fragment)` constructor, which treats `query` as unencoded text and
  re-quotes it, corrupting an already-escaped `%XX` sequence (caught by a
  test with an `&` in the symbol). HTTP 429 →
  `MarketDataUnavailableException`; any other non-2xx status, a connection
  failure, a request timeout, a malformed-URI/build failure, or an
  interrupted request (interrupt flag restored via
  `Thread.currentThread().interrupt()` before rethrowing) →
  `MarketDataResponseException`. A blank/missing API key is rejected
  (`MarketDataUnavailableException`) before any request is sent — the
  server receives nothing. The response body is read in bounded chunks, at
  most `maxResponseSize + 1` bytes, so an oversized response is rejected
  deterministically without buffering an unbounded body.
  `AlphaVantageDailyParser` remains the sole parser; its exceptions
  propagate unwrapped, never re-classified by the adapter.
- **Configuration** (`AlphaVantageProperties`, prefix
  `parallax.alphavantage`, an immutable `@ConfigurationProperties` record):
  `api-key` (`${ALPHA_VANTAGE_API_KEY:}`, blank by default), `base-url`
  (`https://www.alphavantage.co/query`), `connect-timeout` (`5s`),
  `request-timeout` (`30s`), `max-response-size` (`8MB`). Every field
  except `api-key` is validated eagerly in the record's compact
  constructor (`base-url` must parse as an absolute `http`/`https` URL with
  a host; the timeouts and max size must be positive) — a misconfigured
  deployment fails at startup. `api-key` is deliberately unvalidated there:
  a blank key must never fail application startup, since D-33 introduces
  no consumer yet that requires one; `AlphaVantageMarketDataProvider` is
  the sole place that rejects a blank key, at request time.

- **Batch 3 — persistence integration:** `DatasetService` gains a
  constructor-injected `MarketDataProvider` (the sole
  `AlphaVantageMarketDataProvider` bean; no registry/factory, since only
  one implementation exists today) and a new
  `createVersionFromAlphaVantage(owner, datasetId, depth)`, following
  `createVersionFromCsv`'s exact shape: resolve the dataset's own symbol
  and check ownership in a short read-only transaction; call
  `fetchDailyBars` (real network I/O) entirely outside any transaction;
  canonicalize the returned bars with D-32's own
  `DatasetContent.canonicalPrice` (the parser itself performs none) before
  `BarSeries`/`DatasetContent.of`, so identical logical bars from Alpha
  Vantage and a CSV upload hash identically; persist through a new shared
  `persistVersion` helper factored out of D-32's lock/allocate/insert
  sequence, so `createVersionFromCsv` and `createVersionFromAlphaVantage`
  are two callers of one algorithm, never two parallel ones. `source =
  ALPHA_VANTAGE`; `sourceDetail` is exactly what the provider returned,
  never reconstructed; `adjustmentBasis` is always `RAW` (Alpha Vantage's
  `TIME_SERIES_DAILY` is not split/dividend-adjusted), never a
  caller-supplied value for this source. A provider failure, or any
  failure before the write transaction opens, creates no `DatasetVersion`
  and consumes no version number — identical to a failed CSV upload.
  `DatasetSource` gains `ALPHA_VANTAGE`; `V4__allow_alpha_vantage_dataset_source.sql`
  widens `ck_dataset_version_source` to allow it (the only schema change
  in this batch — approved as a deliberate, minimal exception to "no
  migration this batch," since the CHECK constraint would otherwise make
  the feature unable to persist anything at all). No REST endpoint,
  controller, or DTO is added in this batch.
- **Batch 4 — REST endpoint and error boundary:** `POST
  /api/datasets/{id}/versions/alpha-vantage`, body `{"historyDepth":
  "COMPACT"|"FULL"}` (`api.AlphaVantageImportRequest`, a one-field record
  read by the same strict D-30 `StrategyDefinitionCodec.parseRequest`
  reader as every other request body — mirroring `CreateDatasetRequest`).
  `DatasetController` only parses/validates and calls
  `DatasetService.createVersionFromAlphaVantage`: no ownership check of
  its own, no direct `MarketDataProvider` call, no persistence or
  hashing logic — the service remains the sole authority for all three,
  exactly as for CSV upload. The response reuses the existing
  `DatasetVersionResponse` (201 Created, same `Location` convention as
  CSV upload) — no second `DatasetVersion` representation. `ApiExceptionHandler`
  gains one `@ExceptionHandler` per `MarketDataException` subclass, never
  collapsed: `MarketDataRequestRejectedException` and
  `MarketDataCapabilityException` (FULL requested against a compact-only
  account — never a fallback to `COMPACT`) → 422;
  `InvalidMarketDataException` → 422 (with the failing bar's date attached
  when known); `MarketDataUnavailableException` (rate limit, temporary
  control response, or a missing/blank API key — still a runtime
  condition, never a startup failure) → 503; `MarketDataResponseException`
  (malformed body, unrecognized shape, or transport/protocol failure) →
  502. Ownership/not-found (404), version-conflict (409), and integrity
  failure (500) are unchanged from D-32's own mapping — a cross-owner or
  missing dataset never reaches the provider. No API key field exists in
  the request shape (the strict reader rejects one as an unknown
  property); the key is never accepted from the client, logged, or
  echoed back.

**Why:** CLAUDE.md requires market data to be abstracted behind a provider
interface, and requires the engine and its data abstraction to never depend
on a specific external provider; introducing `MarketDataProvider` only now
(D-32 deferred it) is the first point a second, genuinely different source
exists. Splitting parsing (Batch 1, plain Java, exhaustively unit-testable
with local fixtures) from HTTP transport (Batch 2) keeps the one class that
touches the network thin and keeps the parser's own tests free of any
server/timing concerns. Batch 3 reuses D-32's exact persistence algorithm
rather than introducing a second one, and canonicalizes provider bars for
the same reason CSV bars are canonicalized: `DatasetContent` is defined
only over canonical data, and a dataset's identity (its content hash) must
not depend on which source produced it. Batch 4 maps each
`MarketDataException` subclass to its own status rather than one generic
502/500 because the subclasses already encode operationally distinct
situations (a temporary provider condition, a permanent capability limit,
a malformed upstream response, invalid market data) that a client or
operator needs to tell apart.

**Rejected:** retries, throttling, or async HTTP (none are part of the V1
scope, and the JDK `HttpClient` default of blocking/single-attempt is
sufficient); building the request URI with the multi-argument `URI`
constructor (double-encodes an already-percent-encoded query); failing
application startup on a blank API key (D-33 has no consumer yet that
requires one; CLAUDE.md's "keep application startup successful" bar); a
generic HTTP/utility framework or shared abstraction beyond this one
adapter; wrapping `AlphaVantageDailyParser`'s exceptions in a transport-
generic exception (would discard Batch 1's classification); a real Alpha
Vantage smoke test in the automated suite (all HTTP tests run against a
local `com.sun.net.httpserver.HttpServer` fixture); a `MarketDataProvider`
registry/factory (Batch 3 — exactly one implementation exists); a
caller-supplied `adjustmentBasis` or `sourceDetail` for an Alpha Vantage
import (Batch 3 — both are determined entirely by the source and the
provider's own response); a second persistence/hashing code path for
provider-sourced versions (Batch 3 — `persistVersion` is shared); a second
`DatasetVersion` REST representation, an ownership check inside the
controller, a direct controller-to-`MarketDataProvider` call, a fallback
from `FULL` to `COMPACT` on a capability failure, date-range parameters,
retries/throttling/caching/async import jobs, and an endpoint for changing
the API key (Batch 4 — none fit this batch's scope or CLAUDE.md's V1
limits).

**Consequence:** the D-33 Alpha Vantage ingestion path is now end to end —
`AlphaVantageMarketDataProvider` fetches, `DatasetService.createVersionFromAlphaVantage`
persists through D-32's own algorithm, and `POST
/api/datasets/{id}/versions/alpha-vantage` exposes it with the same
ownership, validation, and error-mapping guarantees as CSV upload. No
`BacktestRun` exists to consume a `DatasetVersion` yet; reconciling a
fetched `DailyBars` against dataset identity/provenance (open question
below) remains open.

## Open questions

Not yet decided; not blocking current implementation:

1. **Price adjustment basis** (raw vs split/dividend-adjusted) for
   supplied datasets — a data-layer/provenance decision.
2. **Persisting `Constant(double)`** in the backend: **resolved by D-30**
   — `Double.toString`, as a JSON string, so reloaded strategy versions
   stay exactly equal.
3. **Identical entry/exit conditions** are legal (D-19) but would churn
   (enter, then exit next bar). The engine won't reject them; a backend/UI
   warning may be wanted later.
4. **Average-cost display**: confirmed to live entirely outside the
   engine (D-22); a later backend/reporting layer computes
   `costBasis / quantity` and picks its own precision.
5. **Trading-cost metrics and buy-and-hold benchmark** (D-26): trading-cost
   totals are **resolved by D-27**. The buy-and-hold benchmark is
   **resolved by D-28** as an independent post-run calculation over
   `BarSeries` and `BacktestResult`, entering at the first in-range bar's
   open — not by running a strategy through `Backtester` (the withdrawn
   first attempt; see question 6).
6. **Gap-up rejection under `CashFraction(1)`**: sizing at bar N's close
   (D-7) leaves only the whole-share rounding remainder as slack at bar
   N+1's open. A rise from close to open beyond that remainder rejects the
   BUY (`INSUFFICIENT_CASH`); on a steadily rising series this can delay
   entry, or prevent it for the entire run. This is existing, unchanged
   strategy behaviour, not a defect introduced by any later work — it
   surfaced during the D-27 buy-and-hold review because a benchmark that
   inherits the strategy order model inherits this too. Whether a
   different sizing convention or a warning is warranted is a separate
   decision.
