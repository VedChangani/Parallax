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

## Open questions

Not yet decided; not blocking current implementation:

1. **Price adjustment basis** (raw vs split/dividend-adjusted) for
   supplied datasets — a data-layer/provenance decision.
2. **Persisting `Constant(double)`** in the backend: needs a form that
   round-trips exactly (e.g. `Double.toString`) so reloaded strategy
   versions stay equal.
3. **Identical entry/exit conditions** are legal (D-19) but would churn
   (enter, then exit next bar). The engine won't reject them; a backend/UI
   warning may be wanted later.
4. **Average-cost display**: confirmed to live entirely outside the
   engine (D-22); a later backend/reporting layer computes
   `costBasis / quantity` and picks its own precision.
