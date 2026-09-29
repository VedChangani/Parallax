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
  that bean. **Revised by D-37:** `SeededCurrentUser` is deleted and
  replaced by `AuthenticatedCurrentUser`, exactly as anticipated here —
  every owner-scoped repository/service method, and this paragraph's
  ownership rule, are unchanged.
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
ownership, validation, and error-mapping guarantees as CSV upload.
`BacktestRun` (D-34) now consumes a `DatasetVersion` exactly as stored, with
no trimming, regardless of which `DatasetSource` produced it; reconciling a
fetched `DailyBars` against dataset identity/provenance (open question
below) remains open.

## D-34 Backtest run orchestration, persistence, read-time integrity, and REST API

**Decision:** the first end-to-end consumer of `StrategyVersion`/
`DatasetVersion`: synchronous run orchestration (`BacktestRunService`),
atomic result persistence, read-time structural and cross-field integrity
verification, and the `/api/backtest-runs` REST API. Packages `backtest`
and `api`, alongside the existing ones. Delivered in three batches.

- **Batch 1 — schema and persistence model** (`V5__create_backtest_run.sql`):
  `backtest_run` is an immutable, insert-once JPA entity (`@Immutable`,
  database triggers, no setters, no update/delete path — D-31/D-32's own
  four-layer immutability), built directly from an already-computed
  `BacktestResult`/`PerformanceMetrics`/`BuyAndHoldBenchmark`, never by
  invoking `Backtester` itself. Its strategy and dataset identity are each
  tied to their immutable version by a composite foreign key
  (`(strategy_id, strategy_version_number, strategy_definition_hash) ->
  strategy_version(...)`, and likewise for the dataset) plus an owner
  composite foreign key onto `strategy`/`dataset` (`uq_strategy_id_owner`/
  `uq_dataset_id_owner`), so a persisted run can never reference a version
  or owner inconsistent with what is actually stored. There is no run
  status column — a row is written only once a run has already finished.
  `backtest_equity_point`/`backtest_fill`/`backtest_rejection` are
  immutable children (no surrogate id, natural composite key, plain JDBC —
  D-32's `dataset_bar` pattern), never a JPA entity or association. A
  `Fill`/`OrderRejection`'s triggering `IndicatorSnapshot` is stored as a
  small self-describing JSON array (`IndicatorSnapshotJson`), independent
  of the D-30 codec. `engine_semantics_version` records `Backtester.
  SEMANTICS_VERSION` (currently `1`) alongside every other input, so a
  future reader can tell whether a stored run was produced under the
  chronological/execution semantics this codebase currently implements.
- **Batch 2 — orchestration, transactions, and read-time integrity**
  (`BacktestRunService`): `createRun` calls owner-scoped `StrategyService.
  getVersion`/`DatasetService.getVerifiedSeries` (unchanged 404/500
  semantics), validates the requested range with a dedicated
  `BacktestRangeValidator` (strict raw containment — `config.startDate() >=
  dataset.firstDate()`, `config.endDate() <= dataset.lastDate()`, at least
  one bar inside the range — `BacktestRangeException` otherwise), then runs
  `Backtester.run`/`PerformanceMetrics.of`/`BuyAndHoldBenchmark.of` and maps
  the result into persistence rows — all of this with **no database
  transaction open**. `BacktestRunService` itself carries no
  `@Transactional`; the complete parent-plus-children write happens in
  exactly one short transaction on a separate bean, `BacktestRunWriter`
  (self-invocation cannot make Spring's proxy-based transaction boundary
  real), so a failure anywhere in that write — including a child-row
  insert — rolls back the parent row too: no partial run is ever
  observable. The backend does not duplicate indicator warm-up semantics —
  a range in which the strategy's indicators never become ready is still
  accepted; `Backtester`'s own `firstEvaluableDate` remains the sole
  authority for that. The full verified `BarSeries` is passed to the engine
  exactly as `DatasetService` returns it, never trimmed. `BacktestConfig`'s
  monetary/rate fields are persisted as exact `BigDecimal` (`numeric`
  columns) with no arbitrary magnitude or scale limit — an extreme
  valid-syntax value the engine or PostgreSQL genuinely cannot represent
  may surface as an uncaught 500-class failure rather than being capped.
  Reading is split by cost: `getRun` (one run) reconstructs every child row
  through its own engine constructor and performs the full structural check
  (row shape, ascending equity/fill dates, rejection `seq`/date
  chronology) and cross-field check (stored commission/slippage totals
  equal the exact sum over reconstructed fills; the reconstructed
  closed-trade count agrees with the stored metric; the benchmark's
  `cash + costBasis` equals `initialCapital`; `engine_semantics_version` is
  one this codebase still supports) — `BacktestResultIntegrityException` on
  any failure, never client-facing detail. `listRuns` (an owner's history)
  reads only the `backtest_run` parent row and never verifies a child row,
  so it stays cheap regardless of how many equity/fill/rejection rows exist
  behind it. Stored `PerformanceMetrics`/`BuyAndHoldBenchmark` values are
  historical snapshots: `getRun` never calls `PerformanceMetrics.of(...)`,
  `BuyAndHoldBenchmark.of(...)`, or `Backtester.run(...)`, and never reloads
  the original `DatasetVersion` bars or re-decodes the original
  `StrategyVersion` — a completed run remains readable on its own stored
  integrity even after its inputs are later corrupted or superseded. There
  is no result checksum/hash column: integrity comes from reconstructing
  every value through its own engine type plus explicit cross-field
  arithmetic, not a separate stored digest. Two identical `createRun` calls
  are both accepted and produce two distinct rows — V1 has no idempotency
  infrastructure. The D-30 decimal grammar
  (`StrategyDefinitionMapper.DECIMAL`, made `public` — the only D-30 source
  change) is reused unmodified by `BacktestConfigMapper` for
  `initialCapital`/`commissionPerFill`/`slippageRate`, parsed directly into
  `BigDecimal`, never through a `double`; `MalformedBacktestConfigException`
  (shape) and `InvalidBacktestConfigException` (engine semantics) mirror
  D-30's own client-facing split exactly.
- **Batch 3 — REST API** (`api.BacktestRunController`, base path
  `/api/backtest-runs`): `POST` (create), `GET` (list — exactly `listRuns`,
  no child verification), `GET /{id}` (full detail), and three separate,
  independently-integrity-verified child views — `GET /{id}/equity-curve`,
  `GET /{id}/trades` (via `BacktestRunDetail.trades()`, a thin
  `Trade.fromFills` delegate — no separate backend trade calculation), and
  `GET /{id}/rejections`. Every read endpoint calls the same owner-scoped
  `getRun`; the controller itself never invokes `Backtester`, computes a
  metric, derives a trade independently, touches a repository/entity, or
  performs an ownership check of its own. The create request is read by the
  same strict D-30 `JsonMapper` every other envelope uses
  (`StrategyDefinitionCodec.parseRequest`), so an unknown property
  (including a client-supplied `ownerId`, hash, `engineSemanticsVersion`, or
  status) is rejected before Bean Validation ever runs; `@Positive`/
  `@Min(1)` cover the id/version-number bounds the strict reader has no
  vocabulary for. Response DTOs render every `BigDecimal` as
  `toPlainString()`, every metric `double` as a JSON number, an empty
  `OptionalDouble` as JSON `null`, and never a JPA entity.
  `MalformedBacktestConfigException` → 400, `InvalidBacktestConfigException`/
  `BacktestRangeException` → 422, `BacktestRunNotFoundException` → 404
  (grouped with the existing not-found mapping), `BacktestResultIntegrityException`
  → 500 (logged server-side, generic body — D-30/D-32's own integrity-failure
  contract, never a stack trace, SQL detail, or the wrapped cause's text).

**Why:** synchronous, transaction-scoped execution keeps a run's engine
step reproducible and easy to reason about without introducing a queue,
worker, or status machine V1 does not need (CLAUDE.md). Splitting the write
into its own bean is what makes "engine runs outside any transaction, only
the final write is transactional" a checked, testable fact rather than a
convention that Spring's self-invocation limitation could silently violate.
Reconstructing every stored value through its own engine constructor,
rather than trusting columns or a checksum, reuses the exact validation
`Backtester`/`Portfolio`/`Trade` already define instead of inventing a
parallel one. Making `listRuns` deliberately cheap and `getRun` the only
place verification happens keeps a run's history browsable even if one run
among thousands has a corrupted child row.

**Rejected:** run status/state machine (`PENDING`/`RUNNING`/`FAILED`);
asynchronous or queued execution; idempotency keys/infrastructure; a
result hash/checksum column; recomputing `PerformanceMetrics`/
`BuyAndHoldBenchmark`/`Backtester` on any read; reloading the original
`DatasetVersion` bars or re-decoding the original `StrategyVersion` merely
to display a completed run; trimming the dataset's lookback before handing
it to the engine; an arbitrary magnitude/scale cap on `BacktestConfig`
fields; full child-row integrity verification inside `listRuns`; pagination,
PATCH/DELETE, a retry/rerun/status/export/comparison endpoint (all later
features); a second decimal-grammar implementation for `BacktestConfig`
(the D-30 grammar is reused verbatim).

**Consequence:** D-34 is complete. `AlphaVantageMarketDataProvider` remains
an ingestion-time concern only (D-33) — it is never called during a
backtest run and is not a `Backtester`/`BacktestRunService` dependency of
any kind. Spring Security/authentication and the frontend remain the only
unimplemented pieces of the originally sketched Phase 7 path.

## D-35 Persisted-result read-time verification by recomputation, and the broadened `SEMANTICS_VERSION` scope (Phase 9 Batch 2c)

**Decision:** revises D-34 Batch 2's never-recompute-on-read policy.
`BacktestRunService.getRun` still performs the owner-scoped lookup first
(404 if absent, unchanged), then reconstructs and **independently
recomputes** the stored result rather than trusting it structurally:

- **Referenced identity**, loaded but never reloaded as bars: the
  referenced `StrategyVersion` is now **decoded** (`StrategyService
  .getVersion` — the existing owner-scoped, D-30 hash-verified path), and
  the referenced `DatasetVersion`'s **metadata only** is loaded
  (`DatasetService.getVersion` — never `getVerifiedSeries`, never a
  `DatasetBar` row). A missing, corrupted, unsupported-schema, or
  hash-mismatched referenced strategy version, or a missing referenced
  dataset version, is a **stored-run integrity failure (500)** here —
  never the 404/500 it would be during `createRun`: the run itself was
  found; its *reference* is what failed. Cross-owner access to the run
  itself is unchanged (still the ordinary owner-scoped 404).
- **An actual `BacktestResult`** is reconstructed via the engine's own
  constructor (D-24) from the persisted config/equity/fill/rejection rows
  plus the decoded strategy and dataset symbol — not a parallel "verified
  result" model. Its constructor performs every cross-list structural
  check (equity curve strictly ascending and range-contained, fills
  strictly ascending and alternating BUY/SELL from BUY, rejections
  non-decreasing) as one checked fact, replacing what D-34 Batch 2
  duplicated by hand in the reconstructor.
- **Ledger replay:** a real `Portfolio`, seeded from the persisted
  `initialCapital`, replays every fill in chronological order; each
  resulting `EquityPoint` must equal — by exact record equality, scale-
  sensitive `BigDecimal` — the corresponding persisted equity point.
  Never calls `Backtester.run(...)`; only replays the same engine
  `Portfolio` the original run used.
- **Fill-versus-config verification:** every fill's `fillPrice` must
  equal `referenceOpen` adjusted by the persisted config's own slippage
  formula (D-7/D-23), and its `commission` must equal the persisted
  `commissionPerFill`, exactly.
- **Order id continuity:** `{fill ids} ∪ {InsufficientCash ids}` must be
  exactly `{1..n}` (D-21) — checked explicitly, since neither
  `BacktestResult` nor any per-row constructor enforces it.
- **`firstEvaluableDate` semantics:** empty is incompatible with any fill
  or rejection existing (D-25: no signal before indicators are ready);
  present must name an actual equity-curve date, and no fill/rejection
  signal may predate it. This cannot prove the date is where indicators
  *actually* became ready (that needs the original bars) — only that it
  is not incompatible with what the persisted result already shows.
- **`PerformanceMetrics` is recomputed**, via `PerformanceMetrics.of` on
  the reconstructed result, and compared by exact record equality (bit-
  exact for every `double`/`OptionalDouble`) against the stored metrics —
  never Backtester, never a tolerance/epsilon.
- **The buy-and-hold benchmark** is verified only as far as the persisted
  state allows: its fixed reference point (`cash`/`quantity`/`costBasis`,
  D-28) is paired with the *result's own* (already ledger-verified)
  equity-curve dates and closes into a real `BuyAndHoldBenchmark`, whose
  constructor proves `cash + costBasis == initialCapital`, and whose
  recomputed `totalReturn()` is compared bit-exact against the stored
  value. This cannot independently rederive the entry quantity/cost basis
  from the original first-bar opening price (D-28's option-A entry) —
  that requires the original bars and is out of this verification's reach
  by design.
- **Never rerun**: `Backtester.run(...)`, a market-data provider, and full
  `DatasetBar` loading are never invoked by `getRun` — this is read-time
  verification of an already-completed, persisted result, bounded by
  exactly what that result and its immutable referenced identities
  already contain, not a re-run of the experiment. `listRuns` still
  performs none of this (unchanged, deliberately cheap).
- **`SEMANTICS_VERSION`'s documented scope is broadened** (Phase 9 Batch
  2a; value unchanged, still `1`) to explicitly cover *any* convention
  capable of altering a persisted result for identical inputs: the run
  loop, indicator formulas, sizing/execution, portfolio accounting,
  trading-cost derivation, `PerformanceMetrics` conventions, and
  `BuyAndHoldBenchmark` behavior — not only the run loop and portfolio
  accounting D-34 originally named. One version remains sufficient
  (Option A, not a second version field): every value it covers is now
  independently recomputed and compared on read, so an unversioned
  formula drift would already fail loudly rather than silently.
- **Still no result hash/checksum column** — integrity comes from
  reconstructing and recomputing through the engine's own types, not a
  stored digest that tampering could rewrite alongside the data.

**Why:** the engine's own constructors and functions (`BacktestResult`,
`Portfolio`, `PerformanceMetrics.of`, `BuyAndHoldBenchmark`) are the
existing, already-tested source of truth for every one of these
properties; recomputing through them — rather than re-deriving the same
rules by hand in the reconstructor, or trusting stored values structurally
— is both the smallest correct implementation and the strongest guarantee
available without reloading the original dataset bars.

**Rejected:** rerunning `Backtester.run(...)` on read (would need the
original bars, and cannot verify a run from a semantics version the
codebase no longer implements); loading `DatasetBar` rows for historical
verification (the bars are not required to prove any of the properties
above, and loading them at read time reintroduces the exact cost/coupling
D-34 avoided); a result hash/checksum column; a second `SEMANTICS_VERSION`-
like field for metrics/benchmark conventions specifically; tolerance-based
comparison for any monetary or metric value (a one-cent or one-ulp
difference must fail exactly where the persisted domain is exact).

**Consequence:** a corrupted or tampered persisted result now fails closed
(500) rather than silently returning inconsistent historical data. A
genuinely valid historical run — including every run this codebase could
already produce before this decision, since no prior valid persisted
semantics changed — continues to read back successfully. `getRun` costs a
bounded, small number of additional queries (the referenced strategy
version's decode, the referenced dataset version's metadata) on top of the
existing child-row reads, never proportional to dataset size.

**Revised by Phase 10 Batch 1** (closing a causal-verification gap the
Phase 9 final audit identified): `getRun` additionally verifies, per fill
and rejection, that its signal snapshot's indicator specs exactly match
`strategy.requiredIndicatorSpecs()` (a duplicate stored spec is now
rejected by `IndicatorSnapshotJson.read` directly, never silently
collapsed), that its snapshot `close` equals the result's own equity-curve
close on that date, and that the strategy's own condition actually
evaluates true against that snapshot; that every fill, and every
`InsufficientCash` rejection's `executionDate`, lands strictly after its
signal date and on the equity-curve bar immediately following it (D-7);
that `InsufficientCash.availableCash` equals the cash replayed from fills
before that execution date; and that every ENTER signal (a BUY fill or any
rejection) occurs only while the replayed portfolio is flat, and every
EXIT signal (a SELL fill) only while long. All of this is provable from
already-persisted data alone — no `DatasetBar`, no `Backtester.run(...)` —
and changes no engine behavior and no `SEMANTICS_VERSION`.

## D-36 Defensive numeric input bounds at the JSON/mapper boundary (Phase 9 Batch 2b)

**Decision:** two new, purely defensive bounds on externally supplied decimal
literals, enforced at the JSON→engine mapper boundary — `StrategyDefinitionMapper`
and `BacktestConfigMapper` — never in the engine itself. The goal is bounding
the cost of a hostile input, not narrowing Parallax's mathematical domain: a
valid value accepted before this decision is accepted identically after it,
with identical engine behavior and identical persisted canonical
representation/hash.

- **Raw-length bound:** a decimal literal longer than **100 characters** is
  rejected as malformed (400) — `StrategyDefinitionMapper.MAX_DECIMAL_LITERAL_LENGTH`,
  checked (via the new shared `requireBoundedLength`) before any `BigDecimal`
  or `double` parsing is attempted, ahead of the D-30 grammar regex itself.
  Applies uniformly to every decimal literal parsed by either mapper: `Constant`,
  `CashFraction`, and `BacktestConfig`'s `initialCapital`/`commissionPerFill`/
  `slippageRate`.
- **Canonical precision bound:** for a literal that is parsed into a
  `BigDecimal`, its *canonical* value (`BigDecimal.stripTrailingZeros()` —
  deliberately **not** `BacktestConfig`'s further `setScale(0)` floor for a
  negative scale, which would materialize a huge digit string for an
  astronomically negative scale, exactly the cost this bound exists to avoid)
  may have at most **18 integer digits and 18 fractional digits** (36
  significant digits total) — `MAX_INTEGER_DIGITS`/`MAX_FRACTION_DIGITS`,
  enforced by the new shared `requireWithinCanonicalPrecisionBounds`, a
  semantic-class (422) check layered after a successful parse and before the
  engine constructor's own semantic checks. Digit counts are derived from
  `BigDecimal.precision()`/`scale()` in `long` arithmetic
  (`integerDigits = max(precision - scale, 1)`, `fractionalDigits =
  max(scale, 0)`) — never from `toPlainString()` — so this never
  materializes a large number even when `scale` itself is astronomically
  large (for example `"1e2147483648"`, whose canonical integer-digit count
  is computed without ever building a 2-billion-digit value). Applies to
  `BacktestConfig`'s three fields and to `CashFraction`.
- **`Constant` is deliberately exempt from the precision bound** — length
  only. `Constant` is the one D-30 field that never becomes a `BigDecimal`
  at all: it is parsed straight into a `double` (D-14/D-18's own design,
  predating this decision), evaluated once per bar at O(1) cost regardless
  of magnitude. The precision bound exists to cap the cost of exact
  `BigDecimal` arithmetic — a cost `Constant` never incurs — and applying it
  there would conflict with D-30's explicit, tested guarantee that the
  *full* `double` range (down to `Double.MIN_VALUE` ≈ 4.9E-324, up to
  `Double.MAX_VALUE` ≈ 1.8E308) round-trips exactly through the codec
  (`StrategyDefinitionCodecTest#constantCanonicalFormsMatchDoubleToString`).
  The length bound alone is the correct, non-conflicting defense for this
  field.
- **Failure classification**, preserving the existing malformed/invalid
  split (D-30/D-34) exactly: malformed decimal syntax → 400 (unchanged);
  raw literal over 100 characters → 400 (`Malformed*Exception`, the same
  type the grammar check itself already throws); canonical precision bound
  exceeded → 422 (`Invalid*Exception`, the same type an existing semantic
  rule — range, finiteness — already throws). A value can still
  independently fail the engine's own existing semantic rule (`CashFraction`
  `0 < f <= 1`, `BacktestConfig` positivity/`[0,1)`/date ordering) exactly as
  before; the precision bound is evaluated first only because it runs
  earlier in the same method, not because it supersedes those rules.
- **No change to**: the D-30 decimal grammar; `Constant`/`CashFraction`
  underflow, overflow, `-0.0` folding, canonical JSON emission, or hashing;
  `BacktestConfig`'s own constructor semantics; the engine in any way;
  indicator period bounds (explicitly **no** maximum period — an
  arbitrary trading restriction the design rejects; the Phase 9 Batch 2a
  allocation changes already remove the O(period) memory cost that would
  have motivated one); `SEMANTICS_VERSION`; the database schema (no
  migration).

**Why:** the bound belongs at the mapper boundary (the one place that still
holds the original lexical literal) rather than in the engine, matching
CLAUDE.md's dependency direction and D-30's own "mapper owns syntax, engine
owns semantics" split. Sharing `requireBoundedLength`/
`requireWithinCanonicalPrecisionBounds` as public static members of
`StrategyDefinitionMapper` follows the exact precedent D-30/D-34 already set
for the shared `DECIMAL` grammar pattern, rather than introducing a new
shared utility type for two call sites. Measuring precision via
`precision()`/`scale()` arithmetic rather than `toPlainString()` is what
makes the bound itself safe to evaluate on a pathological input — the same
property it exists to enforce on everything downstream of it.

**Rejected:** applying the precision bound to `Constant` (conflicts with
D-30's tested full-double-range guarantee, and `Constant` never does
`BigDecimal` work in the first place — the actual resource this bound
protects); a maximum indicator period (an arbitrary trading restriction; the
real fix, delivered in Phase 9 Batch 2a, was removing the O(period)
allocation, not capping how far back a strategy may look); a result/request
body size limit (a distinct, transport-layer concern, deferred); a new
generic numeric-bound abstraction shared across unrelated fields beyond the
two genuinely shared call sites; recomputing or re-deriving any existing
D-30/D-34 semantic rule.

**Consequence:** an adversarial decimal literal can no longer force
unbounded `BigDecimal` construction/arithmetic cost through either mapper.
Every value a genuine client could reasonably supply — including every
value any existing test exercised before this decision — remains accepted
with an identical canonical representation and hash.

## D-37 Session authentication, CSRF, and the `AuthenticatedCurrentUser` replacement (Phase 9 Batch 3.1)

**Decision:** Spring Security with a server-side `HttpSession`, cookie-based
SPA CSRF protection, and bcrypt-family password hashing — no JWT, no OAuth,
no API tokens. `CurrentUser` (D-31's seam) gets its production
implementation; every owner-scoped service/repository/controller is
unchanged, exactly as D-31 anticipated.

- **Mechanism:** `spring-boot-starter-security` plus a single
  `SecurityConfig` (`@EnableWebSecurity`, one `SecurityFilterChain` bean).
  Login is Spring Security's own `formLogin`, posted to `/api/auth/login`
  as `application/x-www-form-urlencoded` `username`/`password` — chosen
  over a hand-written JSON login controller because `formLogin` already
  provides session-fixation protection (`changeSessionId()`) and CSRF
  token rotation on authentication; reimplementing either by hand is
  exactly where ad hoc login endpoints go wrong. A custom `successHandler`/
  `failureHandler` pair replaces the default redirect behavior with fixed
  JSON: `200 {"username": ...}` on success, a single generic `401`
  ProblemDetail (`"invalid username or password"`) on any failure. Logout
  (`POST /api/auth/logout`) uses `HttpStatusReturningLogoutSuccessHandler`
  (`204`), invalidates the session, and deletes the `JSESSIONID` cookie.
  `.loginPage("/api/auth/login")` doubles as the login-processing URL,
  which is what suppresses Spring Security's generated HTML login page
  (`isCustomLoginPage()` becomes true, so `DefaultLoginPageGeneratingFilter`
  is never populated) — this backend is a pure JSON API with no
  browser-facing login form.
- **Password storage:** `PasswordEncoderFactories.createDelegatingPasswordEncoder()`,
  storing every hash as `{bcrypt}$2a$...`. `AppUserDetailsService`
  (`UserDetailsService`) throws `UsernameNotFoundException` for both an
  unknown username and a username with a `null` `password_hash`;
  `DaoAuthenticationProvider`'s default `hideUserNotFoundExceptions`
  collapses that, and a wrong password, into the same
  `BadCredentialsException` — a login response can never distinguish "no
  such user" from "this user has no password" from "wrong password".
- **Schema** (`V6__add_app_user_password.sql`): one nullable
  `password_hash varchar(255)` column on `app_user`, plus a CHECK
  enforcing the `{id}encodedHash` format. `NULL` is the single "cannot
  authenticate" state — it covers the seeded `dev` row (never deleted,
  per D-31) and any account an operator disables later. There is no
  `enabled` column and no roles/authorities table: V1 has exactly one
  capability level.
- **`CurrentUser` replacement:** `SeededCurrentUser` is deleted.
  `AuthenticatedCurrentUser` resolves the owner id from
  `SecurityContextHolder`'s authenticated principal — a
  `ParallaxUserPrincipal` (`UserDetails` + `CredentialsContainer`, so
  `DaoAuthenticationProvider` erases the hash from the principal actually
  held in the session) that already carries the `UserId`, so no second
  database lookup happens per request. It never falls back to a default
  identity: no authentication, or a principal of the wrong type, throws
  rather than guessing — unreachable for a protected endpoint under this
  batch's `authorizeHttpRequests` rules, so a thrown exception here
  indicates a wiring defect, not a normal unauthenticated request.
- **Authorization:** `authorizeHttpRequests` is default-deny, evaluated in
  registration order: `permitAll` for exactly `POST /api/auth/login`,
  `GET /api/auth/me`, `GET /actuator/health`, and `/error`; `authenticated`
  for the rest of `/api/**`; `denyAll` for anything else. `401`
  (`ProblemDetailAuthenticationEntryPoint`) is unauthenticated-on-a-
  protected-path — never a redirect, a `WWW-Authenticate` challenge, or an
  HTML page. `403` (`ProblemDetailAccessDeniedHandler`) covers both a
  missing/invalid CSRF token and a `denyAll` path for an authenticated
  user, deliberately with the same generic body. `404` for a
  missing-or-cross-owner resource is entirely unchanged (D-31/D-32/D-34):
  authentication only changes *where* the owner id in `CurrentUser` comes
  from, never how ownership is checked.
- **CSRF:** `csrf().spa()` (Spring Security 7's SPA-oriented cookie
  repository/request handler — a non-`HttpOnly` `XSRF-TOKEN` cookie, an
  `X-XSRF-TOKEN` header, BREACH protection via XOR encoding) applies to
  every state-changing request, including login, logout, (later)
  registration, and every `/api/**` write — never disabled or exempted
  anywhere. A same-origin `CsrfCookieFilter` (the standard filter from
  Spring Security's own SPA CSRF guide, added once directly after
  `CsrfFilter`) forces the deferred token to resolve on every request, so
  the cookie is actually written even though nothing in a pure JSON API
  ever reads the `_csrf` request attribute the lazy default relies on. The
  CSRF cookie's `Secure` attribute is left to `CookieCsrfTokenRepository`'s
  own `request.isSecure()` default rather than tied to
  `PARALLAX_COOKIE_SECURE`: `spa()` does not expose the repository
  instance afterward for further customization, and mirroring the actual
  request scheme is at least as correct as a static flag.
- **Session cookie:** `JSESSIONID` is `HttpOnly`, `SameSite=Lax`, and
  `Secure` unless `PARALLAX_COOKIE_SECURE=false` (for local
  `http://localhost` development) — set entirely through Boot's native
  `server.servlet.session.cookie.*`/`server.servlet.session.timeout`
  properties; no custom code configures the session cookie itself.
  `HttpSecurity`'s baseline `SessionManagementConfigurer` already applies
  `IF_REQUIRED` creation and `changeSessionId()` fixation protection with
  no explicit configuration.
- **No CORS.** The deployment topology is same-origin (the SPA and
  `/api` share one origin — a dev proxy locally, a reverse proxy in
  production); `SameSite=Lax` plus CSRF already blocks cross-origin
  writes, and there is no `CorsConfigurationSource` to permit cross-origin
  reads.
- **Password-claim mechanism:** `PasswordClaimRunner` (an
  `ApplicationRunner`) is the only way, in this batch, to set a password
  on a pre-existing, passwordless account — most importantly the seeded
  `dev` row, which owns every strategy/dataset/run created before
  authentication existed. It runs only when both
  `parallax.auth.claim-username`/`-password` (bound from
  `PARALLAX_CLAIM_USERNAME`/`PARALLAX_CLAIM_PASSWORD`) are set; it never
  creates a user and never overwrites an existing hash (a username already
  claimed is left untouched, logged at INFO); an unknown username or a
  password failing `PasswordPolicy` fails startup outright, with no
  message ever including the password itself. `PasswordPolicy` (≥15
  characters, ≤72 UTF-8 bytes, no composition rules) is factored out now
  purely because the not-yet-implemented registration batch will reuse it
  unchanged.
- **Test infrastructure:** `AuthenticatedMockMvcConfig` (test-only,
  `MockMvcBuilderCustomizer`) makes every `MockMvc` request in an
  importing test authenticated (`.with(user(...))`) and CSRF-exempt
  (`.with(csrf())`) by default, so the pre-existing D-31/D-32/D-34
  controller tests — which already override `CurrentUser` with
  `@MockitoBean` to control ownership directly — keep proving what they
  proved before Spring Security existed, without becoming real-session
  login tests themselves. `AuthenticationIT` is the real-session proof: it
  imports neither `AuthenticatedMockMvcConfig` nor a mocked `CurrentUser`,
  driving the actual filter chain end to end (login, logout, CSRF,
  session rotation, cookie attributes, the generic-failure invariant).

**Why:** a server-side session needs no client-held secret to protect and
gives logout real revocation, unlike a JWT; this application has no
third-party API-client or horizontal-scaling requirement that would justify
JWT's added complexity (key/secret management, no server-side revocation).
`formLogin` over a hand-written login controller avoids re-implementing
session-fixation and CSRF-rotation protection that already exist and are
already tested upstream.

**Rejected:** JWT/stateless auth (no revocation on logout without an
allow/deny-list, and no consumer needs statelessness); OAuth2/OIDC/social
login (no concrete architectural reason, and it adds an external identity
dependency); HTTP Basic (resends credentials every request, browser-cached
with no logout, native popup dialogs); a dev-profile authentication bypass
(a second security mode a production deployment could end up running by
accident — explicitly rejected by the project owner); an `enabled`
column or roles/authorities table (no V1 use case; a `NULL` hash already
expresses "disabled"); auto-login immediately after registration (would
duplicate the session-creation path outside `formLogin` — moot in this
batch, since registration itself is deferred); Spring Session
JDBC/Redis (only needed for multiple instances or session survival across
a restart, neither of which V1 requires).

**Consequence:** every `/api/**` endpoint requires a real authenticated
session; the seeded `dev` user cannot log in until claimed via
`PasswordClaimRunner`. Self-registration (D-38), the frontend identity
lifecycle (D-39), and password change (D-40) were separate, later batches
(3.2/3.3/3.4 respectively) — this decision covers only the backend
authentication core.

## D-38 Public self-registration (Phase 9 Batch 3.2)

**Decision:** `POST /api/auth/register` — always `permitAll`, reusing
every D-37 mechanism unchanged (the D-30 strict codec, `PasswordEncoder`,
`PasswordPolicy`, `ApiExceptionHandler`'s `ProblemDetail` conventions).
Registration is on by default, matching a public application.

- **Request/DTO:** `RegisterRequest(username, password)`, read exactly
  once by `StrategyDefinitionCodec.parseRequest(String, Class)` — the
  same generic strict-envelope overload `CreateDatasetRequest` already
  uses for a request that has nothing to do with strategy definitions.
  An unknown property (`id`, `passwordHash`, `ownerId`, ...) fails before
  Bean Validation ever runs, which is what rules out mass assignment; the
  response echoes only `{"username": ...}`.
- **Username:** `AppUser.USERNAME_PATTERN =
  "^[a-z0-9][a-z0-9._-]{2,63}$"` — lowercase-only (so the case-sensitive
  `uq_app_user_username` constraint also behaves as case-insensitive
  uniqueness), 3–64 characters, matching the `username varchar(64)`
  column exactly at the upper bound. Enforced as a `@Pattern` on the
  envelope record → `ConstraintViolationException` → 400, the same path
  every other envelope constraint already uses.
- **Password:** `PasswordPolicy` (D-37: ≥15 characters, ≤72 UTF-8 bytes,
  no composition rules), reused byte-for-byte, not reimplemented — the
  only reason it was factored out of `PasswordClaimRunner` in D-37. A
  violation throws `WeakPasswordException` → 400 (a shape failure, not a
  422 semantic question — mirrors `MalformedStrategyDefinitionException`'s
  own 400, not `InvalidStrategyDefinitionException`'s 422).
- **`UserRegistrationService`:** the only write path for a new `AppUser`.
  Encodes the password, then a single `AppUserRepository.saveAndFlush`
  inside a try/catch — mirroring `StrategyService.createStrategy`'s exact
  `isConstraint(e, "uq_app_user_username")` idiom (constraint-name
  matching on `DataIntegrityViolationException`'s Hibernate cause, never
  a racy `exists()` pre-check, which cannot see a concurrent writer
  between the check and the insert). `AppUserRepository` gains
  `saveAndFlush` in place of D-37's plain `save`, needed for exactly the
  same reason `StrategyRepository` exposes it: the constraint violation
  must surface synchronously, not at an unrelated later flush.
  Registration never logs the caller in — `AuthController.register`
  returns `201`, and the frontend performs a separate `POST
  /api/auth/login` afterward, so session creation keeps exactly the one
  code path D-37 established (`formLogin`).
- **`parallax.auth.registration-enabled`** (`PARALLAX_REGISTRATION_ENABLED`,
  default `true`): checked inside `UserRegistrationService.register`,
  never at the `SecurityConfig` authorization layer. The endpoint stays
  `permitAll` regardless of the flag — moving it to `denyAll`/
  `authenticated` when disabled would turn an anonymous attempt into a
  generic 401 instead of the specific `RegistrationDisabledException` →
  403 the flag is meant to produce.
- **Errors** (`ApiExceptionHandler`): `WeakPasswordException` → 400 (new
  handler, same `ProblemDetail` shape as every other malformed-request
  case); `DuplicateUsernameException` → 409 (added to the existing
  duplicate-name/version-conflict handler's exception list, unchanged
  otherwise); `RegistrationDisabledException` → 403 (a new handler — the
  first `ApiExceptionHandler`-originated 403 in the backend; every other
  403 comes from Spring Security's filter-level handlers).
- **`AppUser` construction:** a second package-private constructor,
  `AppUser(String username, String passwordHash)`, alongside D-37's
  `assignPassword` path — registration knows the hash at creation time
  and never goes through the "claim a passwordless row later" state
  machine.

**Why:** every mechanism D-38 needs (strict parsing, password hashing,
password policy, constraint-based conflict detection, the `ProblemDetail`
error shape) already exists from D-30/D-31/D-37; registration is
additive wiring, not new architecture.

**Rejected:** an `exists()` pre-check for the duplicate-username case
(D-31 precedent: racy, and the database constraint is authoritative);
disabling the endpoint at the authorization layer when registration is
off (produces the wrong status code, 401 instead of 403); auto-login
after registration (would duplicate `formLogin`'s session-creation path);
email verification, password confirmation, CAPTCHA, or rate limiting at
this layer (out of scope for V1; rate limiting belongs at a reverse
proxy, per D-37's own "No CORS" deployment-topology note).

**Consequence:** an anonymous visitor can create an account and
immediately log in with it. The frontend identity lifecycle (resolved by
D-39) and password change (resolved by D-40) were separate, later
batches.

## D-39 Frontend identity lifecycle: AuthProvider, epoch-based cache isolation, and cross-tab sync (Phase 9 Batch 3.3)

**Decision:** a single `AuthProvider` (React context) owns
`loading`/`anonymous`/`authenticated` state, resolved from `GET
/api/auth/me` on mount; `RequireAuth` gates every protected route;
`httpClient.js` carries `credentials: 'same-origin'` and `X-XSRF-TOKEN` on
every state-changing request; `immutableCache.js` gains an epoch counter
so a response that resolves after a login/logout/401 can never populate
the cache with stale-identity data; a `BroadcastChannel('parallax-auth')`
keeps every open tab's identity in sync.

- **`AuthProvider`/`RequireAuth`/`AuthContext`** (`src/auth/`): `refresh()`
  calls `getMe()` (D-37/D-38's `api/auth.js`, a thin wrapper exactly like
  every other endpoint module) and resolves to `anonymous` on any failure
  — `getMe`/`login` both pass `skipUnauthorizedHandling` so their own
  401s are treated as the expected, normal outcome they are, not a
  session that just expired. `RequireAuth` redirects an anonymous visitor
  to `/login?next=<requested path>`, and `LoginPage` navigates to that
  `next` (or `/`) on success. `/login`/`/register`
  (`src/features/auth/`) are the only public routes; both, and every
  protected page, render inside the same `AppLayout` shell — `Nav` adapts
  by auth status rather than the app having two shells.
- **CSRF/credentials** (`httpClient.js`): every request carries
  `credentials: 'same-origin'` explicitly (never relying on the fetch
  spec's own default); every request but GET/HEAD reads the `XSRF-TOKEN`
  cookie and sends it back as `X-XSRF-TOKEN`, matching D-37's
  `CookieCsrfTokenRepository`/`SpaCsrfTokenRequestHandler` contract
  exactly (the header must carry the *raw* cookie value, never an encoded
  one). A module-level `setUnauthorizedHandler(handler)` — the one
  callback `AuthProvider` registers — is invoked on any 401 except one
  from a call passing `skipUnauthorizedHandling`; this is a plain module
  singleton, not React context, because `httpClient.request` is the one
  non-React fetch call site and every caller (including one outside a
  component, e.g. `useApiResource`) must reach it identically.
- **Cache/identity isolation** (`immutableCache.js`): `bumpEpoch()`
  (called by `AuthProvider` on every login, logout, and unexpected 401)
  clears the store and advances a counter; `set(key, value, atEpoch)`
  silently drops a write whose `atEpoch` no longer matches the current
  epoch. `useApiResource` captures `currentEpoch()` when a fetch starts
  and passes it to the eventual `set` call — the one change needed
  outside the auth code itself, since this hook is the sole caller of
  `immutableCache.set`.
- **Cross-tab sync:** a `BroadcastChannel('parallax-auth')` message
  (`{type: 'identity-changed'}`), posted by `AuthProvider` after every
  login/logout/unexpected-401, is received by every open tab including
  the one that posted it; each reacts by bumping its own epoch and
  re-running `refresh()`. A tab without `BroadcastChannel` (an old
  browser, or a test's jsdom) still works correctly on its own — it just
  cannot announce or hear about a change in another tab.
- **Error display:** `ErrorState` shows a fixed "reload the page" message
  for any `status === 403` (a CSRF/`denyAll` failure is never something a
  retry-the-same-click can fix) instead of the backend's own generic
  `detail`; every other status still shows the backend's own message
  unchanged. A login failure always shows the same fixed message,
  regardless of which of unknown-user/wrong-password/passwordless-account
  actually happened — mirroring D-37's own backend-side generic-failure
  invariant.
- **A real bug this design surfaced:** D-37's `CsrfLogoutHandler` expires
  the `XSRF-TOKEN` cookie along with the session. Without re-establishing
  it, the very next login attempt in the same browser session would
  submit no CSRF token and fail with a 403 (masked by the generic login
  message, since "login failures remain generic" is itself a requirement)
  — found only during manual browser verification, since jsdom's cookie
  jar doesn't model cookie expiry the way a real `Set-Cookie: ...;
  Expires=...` response does. Fixed by having `logout()` call the same
  `refresh()` used on mount immediately after clearing local state, which
  re-triggers the backend's `CsrfCookieFilter` and issues a fresh cookie.

**Why:** every mechanism here is additive wiring onto D-37/D-38's already-
decided backend contract (session cookie, CSRF cookie/header pair,
`ProblemDetail` shapes) — no backend behavior changed to accommodate the
frontend. The epoch mechanism is the smallest correct fix for the actual
race it exists to close (a slow response outliving a login/logout), a
plain counter plus a per-write comparison, not a larger state-management
library or a request-cancellation scheme.

**Rejected:** a stateless axios-style request interceptor library (the
existing single `request()` call site already gives one place to add
CSRF/credentials/401 handling — no second abstraction needed); storing
auth state in `localStorage`/`sessionStorage` (the session cookie is
already the source of truth; mirroring it into browser storage would just
create a second, staler copy that also cannot be `HttpOnly`); a `SharedWorker`
or `localStorage` `storage` event for cross-tab sync instead of
`BroadcastChannel` (equivalent in effect, `BroadcastChannel` is the
simpler, purpose-built API); canceling in-flight requests on logout via
`AbortController` instead of the epoch counter (would require plumbing a
shared abort signal through every call site; dropping a late write is
sufficient since the component holding that stale data is about to
unmount via `RequireAuth` anyway); a two-shell design (a bare login layout
distinct from the authenticated app shell).

**Consequence:** the D-37/D-38 backend authentication core now has a
complete, working frontend. Password change (D-40, decided separately) has
no frontend UI yet — see D-40's own consequence, below.

## D-40 Password change (Phase 9 Batch 3.4)

**Decision:** `POST /api/auth/password` — authenticated, CSRF-protected
(no `SecurityConfig` change: the endpoint simply isn't in the `permitAll`
list, so the existing default-deny rule already covers it). Reuses every
D-37/D-38 mechanism unchanged: the strict envelope reader, the shared
`PasswordEncoder`, and `PasswordPolicy`.

- **Request/DTO:** `ChangePasswordRequest(currentPassword, newPassword)`,
  read once by the same generic strict-envelope overload every other
  request body uses. No `username`/`userId` field — the account changed
  is always the one `CurrentUser` names, so this endpoint can never touch
  another user's password.
- **`PasswordChangeService`:** the only write path that *replaces* an
  existing hash, distinct from D-37's `PasswordClaimRunner` (only ever
  fills a `null` one) and D-38's `UserRegistrationService` (only ever
  creates a new row). Loads the caller's own row via a new
  `AppUserRepository.findById` (the one owner-id-based lookup this
  package needs — never a lookup by any id a request could supply),
  verifies `currentPassword` with `PasswordEncoder.matches`, applies
  `PasswordPolicy` to `newPassword`, then calls `AppUser`'s new
  `changePassword(hash)` mutator (the counterpart to `assignPassword`,
  which only ever fills a `null` hash) and `saveAndFlush`s it.
- **Session rotation:** on success, the controller calls
  `HttpServletRequest.changeSessionId()` — the same fixation-protection
  primitive `formLogin` uses internally. The Servlet API copies the
  session's existing attributes, including Spring Security's
  `SecurityContext`, to the new session id, so the caller stays
  authenticated under the rotated id with no re-login required, while the
  old session id stops working immediately.
- **`WeakPasswordException` gained a `field`:** previously hardcoded to
  `"password"` by the one handler that existed for it (D-38's
  registration); now the exception itself carries the actual field name
  (`"password"` for registration, `"newPassword"` here), and the handler
  reports whichever one it was given.
- **Errors:** a wrong `currentPassword` → `InvalidCurrentPasswordException`
  → 401, with the exact same generic body a failed login gets (D-37's
  `GenericAuthenticationFailureHandler` shape, reused via
  `ApiExceptionHandler` rather than a second filter-level handler, since
  this is an ordinary authenticated `@RestController` endpoint, not part
  of the `formLogin` flow); a `newPassword` failing `PasswordPolicy` →
  `WeakPasswordException` → 400. Both leave the stored hash untouched. The
  response body is empty (`204`) on success — nothing for it to leak.

**Why:** every mechanism this needs (strict parsing, password hashing,
password policy, the generic-auth-failure `ProblemDetail` shape, session
fixation protection) already exists from D-37/D-38 — this is additive
wiring, not new architecture, matching D-38's own precedent.

**Rejected:** a dedicated Spring Security filter/handler for this endpoint
(it is an ordinary authenticated REST call, not part of the
authentication filter chain itself — `ApiExceptionHandler` is the right
layer, exactly like every other domain exception); requiring
re-authentication (a full login) instead of session id rotation (rotation
alone already defeats fixation, and forcing a fresh login for a password
change the user just proved they can make is unnecessary friction); a
separate password-history/reuse-prevention table (no V1 requirement, and
it would be its own password model, which this decision explicitly avoids
creating).

**Consequence:** Phase 9 Batch 3 (D-37 backend authentication core, D-38
self-registration, D-39 frontend identity lifecycle, D-40 password
change) is complete. No frontend UI calls this endpoint yet — that is
deferred, not scheduled as a numbered batch.

## D-41 Profit factor and the per-point drawdown series (V1.1 Batch 3)

**Decision:** `PerformanceMetrics` gains a tenth component, `OptionalDouble
profitFactor` (revising D-26's "nine components"; the structural test now
pins ten), and a public static `drawdownSeries(List<EquityPoint>)`.

- **Profit factor** = gross profit of winning closed trades / absolute
  gross loss of losing closed trades. Closed trades only; win = P&L > 0,
  loss = P&L < 0; breakeven and open trades contribute to neither.
  Empty iff no losing closed trade (never `Infinity`); exactly `0.0` when
  there are losses and no wins. Sums are exact `BigDecimal`; the single
  division is in `double` (D-14). Invariant: present iff `averageLoss` is
  present.
- **Drawdown series** = `(runningPeak - equity) / runningPeak` per equity
  point, a fraction `>= 0` (`0.0` at a new high), index-aligned with the
  curve. `maxDrawdown` is now computed as the maximum of this series, with
  identical arithmetic (bit-identical result). The frontend plots it
  negated so zero is "no drawdown" and negative is drawdown.
- **No migration, no new persisted column.** `profitFactor` is a pure
  function of the closed trades, which the ledger replay already verifies,
  so `BacktestResultReconstructor` uses the recomputed value (nothing
  stored to compare) while every other metric is still compared exactly
  against its persisted column (D-35). The drawdown series is derived on
  read from the verified equity curve and exposed as a `drawdown` number on
  each `GET /{id}/equity-curve` point, so dates align by construction.
  Runs persisted before this batch therefore gain both, retroactively.
- No change to `SEMANTICS_VERSION`: no simulation, execution or existing
  metric semantics changed.

## D-42 CSV export of a completed run (V1.1 Batch 4)

**Decision:** two read-only endpoints on the run, `GET
/api/backtest-runs/{id}/equity-curve.csv` and `.../trades.csv`, each behind
the same owner-scoped, integrity-verifying `getRun` as every other read.
`BacktestCsv` only formats the exact response records the JSON endpoints
return, so a cell is character-for-character the JSON value; nothing is
recomputed or reparsed, no backtest is re-run, and there is no new table,
migration or persisted state.

- **Format (RFC 4180):** UTF-8 without BOM; comma separator; `CRLF` after
  every record including the last; one header row; a field is quoted only if
  it contains a comma, quote, CR or LF (quotes doubled). ISO-8601 dates,
  the backend's exact decimal strings for money, an empty field where a
  value does not apply (never a placeholder). Deterministic: output is a
  pure function of the run.
- **Equity CSV** columns: `date, equity, cash, quantity, close,
  market_value, cost_basis, realized_pnl, unrealized_pnl,
  benchmark_equity, drawdown` (one row per equity point). `drawdown` is the
  D-41 fraction `>= 0`, as a plain decimal without exponent or trailing
  zeros (`0`, `0.25`, `0.0001`).
- **Trades CSV** is a separate file, one row per trade, so the equity file
  never repeats a trade across rows: `status, quantity, entry_order_id,
  entry_date, entry_price, entry_commission, exit_order_id, exit_date,
  exit_price, exit_commission, realized_pnl, total_commission,
  total_slippage_cost, mark_date, mark_close, market_value,
  unrealized_pnl`. An open trade leaves `exit_*` and `realized_pnl` empty and
  fills `mark_*`/`market_value`/`unrealized_pnl`; a closed trade leaves
  those four empty. Per-trade signal indicators are omitted (nested,
  variable-length data does not fit a flat schema).
- **Cells are never free text** (only numbers, dates, enum names), so there
  is no spreadsheet formula-injection surface.
- **Frontend:** `ExportActions` (two low-emphasis buttons in the run page
  header) fetches the file on click and saves it unchanged through a
  temporary object URL. The endpoints set `text/csv` on the response instead
  of `produces`, so a 404/500 stays an ordinary ProblemDetail.

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
