# Parallax Architecture

## Overview

Parallax is a modular monolith with three independently understandable parts:

```
frontend (React/Vite)
        |
        v
backend (Spring Boot)
        |
        v
engine (plain Java)
```

Requests flow from the frontend to the backend, and the backend orchestrates
the engine. Dependencies only point downward. The engine never depends on the
backend, and the backend never depends on the frontend.

## Modules

### engine

The engine is the backtesting core: chronological market data processing,
strategy evaluation, order execution, portfolio accounting, and performance
metrics.

The engine is plain Java, built and tested independently of Spring. It has no
dependency on Spring, Spring Data, JPA, Hibernate, PostgreSQL, REST, HTTP, or
React.

This independence exists so that:

- backtesting correctness can be verified with fast, deterministic unit
  tests that do not require a Spring context, a database, or a running
  server
- the engine's core logic is not accidentally coupled to persistence or web
  concerns
- the engine could, in principle, be reused outside of a web application
  (e.g. from a CLI or a batch job) without dragging in Spring Boot

### backend

The backend is a Spring Boot application. It is responsible for:

- exposing REST APIs to the frontend
- persisting application data (strategies, experiments, results) once those
  entities are introduced
- orchestrating engine runs: constructing engine inputs, invoking the
  engine, and storing/returning engine outputs
- market data provider integration (introduced in a later milestone)

The backend depends on the engine module. The backend does not implement
backtesting logic itself; it delegates to the engine and handles everything
around it (HTTP, persistence, request/response shaping).

### frontend

The frontend is a React/Vite application. It is responsible for presenting
strategies, experiments, and backtest results, and for issuing requests to
the backend's REST APIs. It contains no backtesting logic.

## Why Spring Boot orchestrates rather than implements the engine

Spring Boot is a framework for building applications: web endpoints,
dependency injection, persistence, configuration. Backtesting correctness is
a domain problem, not a web-application problem. Keeping the engine free of
Spring means:

- engine tests run in milliseconds with plain JUnit, with no application
  context to start
- the engine's public API is deliberately small and explicit, rather than
  being whatever shape a framework annotation happens to produce
- a change to the web layer (e.g. how a REST endpoint is versioned) can
  never silently change backtest results

## Why this is a monorepo with separate application modules

Parallax is one product with three parts that change at different rates and
have different responsibilities. A monorepo keeps them versioned together
and lets the backend depend on the engine as an ordinary Maven module,
without the operational overhead of publishing and consuming a separate
artifact repository. Root-level Maven aggregation (`engine` + `backend` as
modules of a root `pom.xml`) builds and tests both Java modules with a
single command, while the frontend remains a separate Node/Vite project
under the same repository root.

## Why Parallax is a modular monolith, not microservices

See [decisions.md](decisions.md).

---

# Engine Architecture (V1 — approved design, partially implemented)

Status: approved design. `data`, `indicator` (including `IndicatorSnapshot`),
`strategy` (`Operand`, `Operator`, `Condition`, `PositionSizing`,
`StrategyDefinition`, `SignalType`, `SignalEvent`), and `execution`
(`OrderSide`, `Order`, `Fill`, `RejectionReason`, `OrderRejection`) are
implemented; `Backtester` and the remaining packages below are not yet
implemented. Decisions referenced as D-n are in
[decisions.md](decisions.md); project state is tracked in
[docs/progress.md](progress.md).

## 1. Shape

```
BacktestRequest (BarSeries, StrategyDefinition, BacktestConfig)
        |
        v
Backtester.run(...)
        |
        v
BacktestResult
        |
        v
PerformanceMetrics.of(result)      [later]
```

- `Backtester` is stateless. It holds no persistent fields and is
  re-entrant. Each backtest creates its own mutable runtime state.
- Processing is single-threaded and single-pass over an immutable,
  pre-validated `BarSeries`.
- Metrics are separate post-processing logic. They are not calculated
  inside the chronological loop.

Future packages (created only when code for them is written):

```
in.vedchangani.parallax.engine             Backtester
in.vedchangani.parallax.engine.data        Bar, BarSeries
in.vedchangani.parallax.engine.indicator   IndicatorType, IndicatorSpec, Indicator, IndicatorSnapshot
in.vedchangani.parallax.engine.strategy    StrategyDefinition, Condition, Operand, Operator, PositionSizing, SignalType, SignalEvent
in.vedchangani.parallax.engine.execution   Order, OrderSide, Fill, RejectionReason, OrderRejection
in.vedchangani.parallax.engine.portfolio   Portfolio, PortfolioState, EquityPoint
in.vedchangani.parallax.engine.result      BacktestConfig, BacktestResult, Trade
```

## 2. Domain model

### Bar

Immutable record: `LocalDate date`, `BigDecimal open`, `BigDecimal high`,
`BigDecimal low`, `BigDecimal close`, `long volume`.

Validation: date non-null; all prices > 0; `low <= min(open, close)`;
`high >= max(open, close)`; `volume >= 0`.

Invalid market data fails fast. Bars are never reordered, deduplicated,
repaired, clamped, or silently skipped.

### BarSeries

Immutable value object: `String symbol`, `List<Bar> bars`.

Validation: symbol non-null and non-blank; bars non-null and non-empty;
defensive copy; unmodifiable exposure; strictly ascending dates (duplicate
or out-of-order dates rejected).

`BarSeries` does not know about `BacktestConfig`, start/end date semantics,
strategies, execution, or data providers. A series may contain pre-start
lookback bars and bars after `endDate`.

### IndicatorSpec

Immutable definition: `IndicatorType type` (`SMA`, `EMA`, `RSI`) and
`int period`. Specs are compared by value, so one distinct spec maps to one
runtime indicator.

### Indicator

Per-run mutable runtime object with a minimal contract:

```
void    update(BigDecimal close)
boolean isReady()
double  value()
```

Indicators process one close at a time and cannot see the `BarSeries` or
future data.

### IndicatorSnapshot

Immutable snapshot of indicator values as of bar N close. It is the only
market-derived input visible to strategy evaluation.

Record `(LocalDate date, BigDecimal close, Map<IndicatorSpec, Double>
values)`. It holds only ready, finite values, stored as a defensively
copied, unmodifiable map in canonical order (type declaration order, then
period). `value(spec)` fails fast for a missing spec. See D-17.

### StrategyDefinition

Structured immutable definition: entry condition, exit condition, position
sizing. No arbitrary executable user code. Full design after Operand and
Condition below, since it is built from them (see D-19).

### Operand and Condition (package `strategy`, see D-18)

The whole V1 strategy grammar is two sealed interfaces and one enum. Each
permitted implementation is a record nested inside its interface:

```
sealed interface Operand   { double  resolve(IndicatorSnapshot s); }
  record IndicatorRef(IndicatorSpec spec)    -> s.value(spec)
  record Close()                             -> s.close().doubleValue()
  record Constant(double value)              -> value

enum Operator { GT, LT }                     GT: left > right, LT: left < right

sealed interface Condition { boolean evaluate(IndicatorSnapshot s); }
  record Compare(Operand left, Operator operator, Operand right)
  record All(List<Condition> conditions)     logical AND, short-circuit, list order
  record Any(List<Condition> conditions)     logical OR,  short-circuit, list order
```

It is referenced as `Operand.Close`, `Condition.All` and so on.

Examples:

```
SMA(20) > SMA(50):
  Compare(IndicatorRef(SMA,20), GT, IndicatorRef(SMA,50))

SMA(20) > SMA(50) AND RSI(14) < 70:
  All([ Compare(IndicatorRef(SMA,20), GT, IndicatorRef(SMA,50)),
        Compare(IndicatorRef(RSI,14), LT, Constant(70)) ])
```

Evaluation boundary:

- Evaluation is a pure function of the condition tree and one
  `IndicatorSnapshot`.
- Nothing in the grammar can reach `BarSeries`, `Bar`, runtime
  `Indicator`s, `Portfolio`, orders, the clock or I/O. The only input is
  the snapshot, and no record holds any other state.
- Every comparison is a `double` comparison (D-14). `Close` converts the
  `BigDecimal` close with `doubleValue()` at resolve time.

Composition:

- `All` and `Any` hold `List<Condition>`, so they nest (for example
  `A AND (B OR C)`).
- Trees are built bottom-up from immutable records, so they are finite and
  acyclic, and evaluation always terminates.
- There is no depth limit, no variables, no arithmetic and no functions.

Construction-time validation (fail fast):

| Type | Rule | Exception |
|---|---|---|
| `IndicatorRef` | `spec` non-null (spec validity is already guaranteed by `IndicatorSpec`) | NPE |
| `Constant` | value finite (no NaN / ±Infinity); `-0.0` normalized to `0.0` | IAE |
| `Compare` | `left`, `operator`, `right` non-null | NPE |
| `All` / `Any` | list non-null; no null elements (`List.copyOf`) | NPE |
| `All` / `Any` | list non-empty | IAE |

`Compare` does **not** reject a `Constant` vs `Constant` comparison or
`left.equals(right)`. Both are accepted, well-defined, deterministic
expressions — their evaluated result just never depends on the snapshot.
Rejecting them would be extra validation for a case that is harmless to
evaluate and costs nothing to allow; the grammar does not try to detect
"this condition happens to always be true/false" in general, and singling
out these two shapes would be arbitrary.

Rules for `All` and `Any`:

- An empty `All` or `Any` is rejected. `All([])` would be vacuously true
  and enter on every bar. `Any([])` would never trade. Either one is almost
  certainly a configuration error, so neither is given an arbitrary
  meaning.
- A single-child `All`/`Any` is allowed. It is logically the child itself.

Missing indicator:

- An `IndicatorRef` whose spec is absent from the snapshot propagates the
  snapshot's `IllegalArgumentException` unchanged. It is not translated or
  pre-validated in the grammar.
- Prevention is structural. The Backtester creates one runtime indicator
  per spec referenced by the `StrategyDefinition`, so a miss inside the
  engine means an engine bug, not bad user data.
- Collecting the referenced specs (an exhaustive traversal over the sealed
  types) is designed with `StrategyDefinition`.

Non-finite values:

- NaN cannot reach a comparison. Snapshot values are finite (D-17),
  `Constant` rejects non-finite values, and a positive `BigDecimal` close
  converts to a positive finite double or at worst `+Infinity`, never NaN.

Equality is structural record equality:

- `All([A, B])` ≠ `All([B, A])` even though they are logically
  equivalent. Order is part of the definition and is never canonicalized.
- Duplicate children are allowed.
- Record `toString()` is deterministic, because the trees contain no maps.

V1 semantics to keep in mind:

- Conditions are **state** conditions, not **events**.
  `SMA(20) > SMA(50)` means "is above", not "crossed above".
- Because entry is evaluated whenever the strategy is flat, a strategy may
  enter on the first evaluable bar of a trend that was already in
  progress.
- There are no `>=`, `<=`, `==`, `NOT` or crossover operators.

### PositionSizing and StrategyDefinition (package `strategy`, see D-19)

```
sealed interface PositionSizing
  record CashFraction(BigDecimal fraction)   0 < fraction <= 1; stripTrailingZeros() normalized

record StrategyDefinition(Condition entryCondition, Condition exitCondition,
                          PositionSizing positionSizing)
  List<IndicatorSpec> requiredIndicatorSpecs()   derived, not stored; canonical order
```

**PositionSizing.CashFraction** requests using `fraction` of the cash
available when the entry signal is created, at the signal bar's close. It
is a configuration value only — it says nothing about whole-share order
quantity, commission, slippage, or next-bar affordability. Translating a
sizing request into an executable order is a Backtester/execution
concern (D-7); `PositionSizing` never gains quantity-calculation logic.
`BigDecimal.compareTo` validates the bounds; the stored value is
`stripTrailingZeros()`-normalized so `0.5` and `0.50` are the same value
(consistent with D-18's `Constant` `-0.0` normalization).

**StrategyDefinition** pairs an entry condition, an exit condition, and a
sizing rule. It describes intent only:

- `entryCondition`: flat → long intent, evaluated when flat.
- `exitCondition`: long → flat intent, evaluated when long.

It holds no runtime state — no current position, pending order, cash,
price, or portfolio; whether the strategy is flat or long, whether an
order can execute, and cash availability are Backtester/Execution/
Portfolio concerns, not this type's. Validation is structural only (the
three components are non-null); this is not a strategy-quality validator.
All of the following are legal:

- Close-only or Constant-only conditions
- conditions with no indicators at all
- identical entry and exit conditions
- the same `IndicatorSpec` referenced by both conditions
- several periods of one indicator type

**`requiredIndicatorSpecs()`** returns every `IndicatorSpec` referenced by
`entryCondition` or `exitCondition` — distinct, and in the same canonical
order `IndicatorSnapshot` uses (indicator type declaration order, then
period ascending). It is computed on demand by a private recursive walk
over the sealed `Condition`/`Operand` types (`Compare` → its operands,
`All`/`Any` → each child, `IndicatorRef` → its spec, `Close`/`Constant` →
nothing), collected into a `TreeSet` ordered by a private comparator and
returned as `List.copyOf(...)`. It is **not** stored as a record
component and does **not** participate in `StrategyDefinition` equality:
equality is default record equality over `(entryCondition, exitCondition,
positionSizing)` alone, so it stays exactly as reproducible as the
conditions and sizing that define it. Both traversal switches are
exhaustive over the sealed hierarchies with no `default` branch, so a
future grammar addition fails to compile here until discovery is
explicitly updated for it.

### SignalEvent and Order (packages `strategy` / `execution`, see D-20)

```
enum SignalType { ENTER, EXIT }

record SignalEvent(SignalType type, IndicatorSnapshot snapshot)
  LocalDate date()   -> snapshot.date()          // derived, not stored

enum OrderSide { BUY, SELL }

record Order(int id, long quantity, SignalEvent signal)
  OrderSide side()   -> ENTER -> BUY, EXIT -> SELL   // derived, not stored
```

**SignalEvent** means: "at this bar's close, the strategy condition
evaluated to an actionable ENTER/EXIT intent." It is not an order, a fill,
a trade, or a portfolio mutation, and it carries no quantity, price, or
guarantee of execution. It preserves the exact immutable snapshot that
caused it, so a later `Fill`/`OrderRejection` can recover the explanation.
Its date is **derived** from the snapshot rather than stored separately —
a signal has no meaningful date other than the bar close that produced
it, and storing a second copy would let it disagree with the snapshot's
own date. `SignalEvent` holds no `StrategyDefinition`, `PositionSizing`,
position/portfolio state, `Order`, or runtime `Indicator`.

**Order** is an immutable queued command, created from a `SignalEvent` at
that signal's bar close, that executes no earlier than the next available
in-range bar's open. It carries only what it needs and nothing
recoverable from the signal or from the future:

| Excluded field | Why |
|---|---|
| creation date | equals `signal.date()` |
| reference close | equals `signal.snapshot().close()` |
| execution date, fill/requested price | unknown at N close — future information |
| status (`PENDING`/`FILLED`/`REJECTED`) | the eventual `Fill`/`OrderRejection` *is* the outcome; pending-ness is the Backtester's local variable |
| `OrderSide` as a stored field | derived from `signal.type()`, so an order can never disagree with the signal that produced it (V1 is long-only, D-16, so the mapping is fixed and one-to-one) |

`id` is a run-local, sequential, per-backtest `int` (`>= 1`), assigned by
the Backtester only when an `Order` is actually created — a
`ZERO_QUANTITY` rejection consumes no id:

```
int id = nextOrderId;
Order order = new Order(id, quantity, signal);
nextOrderId++;
```

`Order` itself validates only `id >= 1`; sequentiality is a Backtester
property. `quantity` is not computed by `Order` — the Backtester sizes
ENTER orders at N close (D-7) and EXIT orders as the full held position;
`Order` validates only that it is positive. Orders are not part of the
result (D-12).

Lifecycle and ownership:

```
bar N close: signal decided (strategy) -> sized (Backtester, D-7) -> Order created (Backtester assigns id) -> pendingOrder
bar N+1 open: execution step decides affordability -> Fill or INSUFFICIENT_CASH OrderRejection; pendingOrder cleared
```

| Concern | Owner |
|---|---|
| deciding a signal | Backtester, using `StrategyDefinition` conditions |
| computing quantity | Backtester sizing step (D-7) |
| validating quantity > 0 | `Order` constructor (Backtester routes `<= 0` to a rejection first) |
| assigning order ID | Backtester run-local counter |
| holding pending state | Backtester's `pendingOrder` |
| affordability, slippage, commission | execution step at N+1 open |

### Fill and OrderRejection (package `execution`, see D-21)

```
record Fill(int orderId, LocalDate date, long quantity, BigDecimal referenceOpen,
           BigDecimal fillPrice, BigDecimal commission, SignalEvent signal)
  OrderSide side()          -> OrderSide.forSignal(signal.type())              // derived
  BigDecimal slippageCost() -> |fillPrice - referenceOpen| * quantity          // derived, exact

enum RejectionReason { ZERO_QUANTITY, INSUFFICIENT_CASH }

sealed interface OrderRejection { SignalEvent signal(); LocalDate date(); RejectionReason reason(); }
  record ZeroQuantity(SignalEvent signal)
    date() -> signal.date()                          // no Order ever existed
  record InsufficientCash(int orderId, LocalDate date, long quantity,
                          BigDecimal requiredCash, BigDecimal availableCash, SignalEvent signal)
    requires requiredCash > availableCash
```

**Fill** is the executed result of an `Order` at the next in-range bar's
open. `date` is that execution bar's date, `referenceOpen` its actual
open, and `fillPrice` the open already adjusted for slippage by the
execution step — `Fill` performs none of that calculation itself; it
represents an already-computed result immutably. `orderId` and `signal`
are copied from the originating `Order`; the `Order` object itself is
never referenced (D-12: orders are not part of the result). Everything
the order's explanation needs is still recoverable without it: order ID
via `fill.orderId()`, signal type via `fill.signal().type()`, the
triggering snapshot via `fill.signal().snapshot()`, and the signal date
via `fill.signal().date()`. `side()` and `slippageCost()` are derived,
not stored, so a `Fill` can never disagree with the signal that produced
it or record an inconsistent slippage figure; `slippageCost()` is exact
bookkeeping over the already-recorded prices, not the execution
calculation that derives `fillPrice` from the configured slippage rate.
`commission == 0` and a `fillPrice` equal to `referenceOpen` (zero
slippage cost) are both valid. `Fill` validates only its own fields
(positive prices, non-negative commission, and so on) — not that the
price matches the configured slippage rate, that cash was sufficient, or
that execution occurred after the signal; those are execution/Backtester
concerns.

**OrderRejection** is a sealed interface with exactly two shapes for the
two genuinely different rejection cases:

- **`ZeroQuantity`**: at signal time, sizing produced a non-positive
  quantity. No `Order` was ever created, so no order ID was consumed and
  there is nothing order-derived to carry — no `requiredCash`, no
  `availableCash`, no sentinel values. Its date equals the signal date,
  derived rather than duplicated, since nothing happens between the
  signal and this rejection.
- **`InsufficientCash`**: an `Order` existed, but at the execution bar's
  open the whole BUY (`quantity × fillPrice + commission`) exceeded
  available cash, so the entire order is rejected — no partial fills.
  `orderId` is the id that order had already been assigned; carrying it
  is what explains a gap in the fill IDs. `date` is the execution bar's
  date, genuinely different from the signal date and therefore stored,
  not derived. `requiredCash` and `availableCash` are supplied
  already-computed by the execution step; the constructor only checks
  `requiredCash > availableCash`, so an instance can never claim
  insufficiency while showing enough cash.

Both records require an `ENTER` signal: a SELL always sells the full held
position and needs no cash, so neither rejection case can happen for one,
and V1 has no SELL-rejection type. `reason()` is derived per record
(`ZERO_QUANTITY` / `INSUFFICIENT_CASH`), not stored, giving a flat
discriminator without duplicated state.

Every generated `SignalEvent` has exactly one terminal outcome: a `Fill`,
a `ZeroQuantity`, or an `InsufficientCash`. This is a documented
invariant, tested at the Backtester level, not a generic `Outcome`
wrapper type — the result's `fills` and `rejections` lists already
express it. Because only created orders consume IDs, and each one ends in
exactly one `Fill` or one `InsufficientCash`, `{fill IDs} ∪
{InsufficientCash IDs}` is always exactly `{1..n}`: a gap in the fill IDs
is expected, deterministic behavior, fully explained by the matching
`InsufficientCash` rejection. For example, order 1 → `Fill`, order 2 →
`InsufficientCash`, order 3 → `Fill`: fills show IDs 1 and 3, and the
rejection shows ID 2.

### Portfolio

Mutable per-run accounting state. Owns cash, position quantity, total cost
basis, realized P&L. Mutated only through `Fill` application.

### Trade

Immutable, derived after the run from fills. A closed trade has an entry
fill, an exit fill, and net realized P&L. An open trade has an entry fill
and no exit fill. Signal reasons are recovered from the fills. There is no
mutable open-trade tracker.

### EquityPoint

Immutable record: date, cash, position quantity, close, position value,
equity. One point per in-range bar.

### PortfolioState

Immutable final-state record: cash, position quantity, cost basis, realized
P&L, last close, unrealized P&L, equity.

### BacktestConfig

`BigDecimal initialCapital`, `BigDecimal commissionPerFill`,
`BigDecimal slippageRate`, `LocalDate startDate`, `LocalDate endDate`.

Both `startDate` and `endDate` are inclusive. The engine owns the semantic
meaning of this evaluation range. There is no lookback-count field: the
lookback is whatever the supplied series contains before `startDate`.

### BacktestResult

Config echo, symbol, `firstEvaluableDate`, fills, rejections, trades,
equity curve, final `PortfolioState`. All output is immutable.

### Rejected and deferred abstractions

Not created in V1: Instrument abstraction; provider abstraction inside the
engine; `IndicatorValue` wrapper; `Strategy` interface; `StrategyContext`
service; general expression language; separate persisted signal list;
order list in the result; mutable open-trade tracker; crossover
abstraction; `NOT` operator; arithmetic operands; `>=` / `<=` / `==`;
`BigDecimal` constants; external condition evaluator/visitor; condition
canonicalization (reordering `All`/`Any` children); discovery methods on
`Operand`/`Condition` (D-19 keeps indicator-spec discovery out of the D-18
evaluation grammar); a general visitor framework for the strategy grammar;
an `IndicatorSpecCollector` class; storing `requiredIndicatorSpecs()` as a
`StrategyDefinition` field; other position-sizing modes (fixed shares,
fixed notional, percent-of-equity, volatility sizing, leverage,
pyramiding); a stored `SignalEvent` date separate from the snapshot's; a
stored `Order` side separate from the derived signal-type mapping; `Order`
status fields (`PENDING`/`FILLED`/`REJECTED`); a mutable `Order`; UUID or
database order IDs; a static/global order-id counter; a `Fill` holding its
originating `Order` directly; a stored `Fill` side or stored
`slippageCost`; a single `OrderRejection` record with a nullable order ID
or nullable cash fields; sentinel/fake `requiredCash`/`availableCash`
values for `ZeroQuantity`; a generic `Outcome`/`SignalOutcome` wrapper
type; a SELL rejection type; rejection reasons beyond `ZERO_QUANTITY` and
`INSUFFICIENT_CASH` (invalid price, market closed, liquidity, broker
error, and so on); execution-causality validation inside `Fill`/
`OrderRejection` (covered by Backtester tests instead); limit orders; stop
orders; order book; broker model; event bus;
partial strategy exits; multiple simultaneous positions;
metrics inside the loop.

The provider abstraction belongs to the backend. The engine receives a
validated `BarSeries`.

## 3. Chronological execution contract

Definitions:

- **lookback bar**: `date < startDate`
- **in-range bar**: `startDate <= date <= endDate`
- **last in-range bar**: the in-range bar with the greatest date

Setup:

- validate the request
- create `Portfolio(initialCapital)`
- create one runtime `Indicator` per spec in
  `strategy.requiredIndicatorSpecs()`, in that canonical order
- pending order is initially null
- sequential order ID starts at 1

For each bar N in chronological order:

```
0. If N.date > endDate: stop processing immediately.
   Bars after endDate must not affect the result.

1. Execute the pending order at N.open, if one exists.
     BUY:  fillPrice    = open × (1 + slippageRate)
           requiredCash = quantity × fillPrice + commission
           if requiredCash > available cash: reject the entire order
           (OrderRejection.InsufficientCash, carrying the order's id).
           Do NOT reduce quantity.
     SELL: fillPrice = open × (1 − slippageRate)
           sell the full held position.

2. On a successful fill: apply the Fill to the Portfolio, append the
   Fill, clear the pending order.

3. Update every referenced indicator using N.close.
   This happens for both lookback and in-range bars.

4. If N.date < startDate (lookback bar):
   no signals, no orders, no fills, no equity points. Continue.

5. Record an EquityPoint at N.close, after any N.open execution.

6. Evaluate the strategy only when:
     - all referenced indicators are ready
     - N is not the last in-range bar
     - a next available bar exists inside the requested range
   The strategy sees only IndicatorSnapshot values as of N.close.

7. If flat and the entry condition is true:
     create an ENTER SignalEvent.
     quantity = floor( (cash × fraction − commission)
                       / (close_N × (1 + slippageRate)) )
     if quantity <= 0: record OrderRejection.ZeroQuantity (no order id consumed).
     otherwise:        create a pending BUY order (assign the next order id).

8. If long and the exit condition is true:
     create an EXIT SignalEvent.
     create a pending SELL order for the full held quantity.

9. Pending orders execute at the next available bar's open.
   Non-trading dates are never invented.
```

## 4. Boundary behavior

- There is never a fill outside `[startDate, endDate]`.
- No signal is evaluated on the last in-range bar, so no order can remain
  pending at the end of the run.
- If `endDate` falls on a non-trading day, the last available in-range bar
  is used.
- Bars after `endDate` are ignored.
- If the series has no bar within `[startDate, endDate]`, the request is
  rejected before execution.
- The first in-range equity point equals `initialCapital`, because no
  lookback bar can generate a signal.
- An open position at the end is not forcibly liquidated. It is marked to
  the last in-range close.
- `firstEvaluableDate` is the first in-range bar at which all referenced
  indicators are ready.

## 5. Look-ahead protection

Strategy evaluation structurally prevents future-data access.

- The strategy receives only an `IndicatorSnapshot`. It does not receive
  the `BarSeries`, future bars, an index position, the `Portfolio`, or
  pending orders.
- Indicators receive exactly one close at a time.
- The fill at N.open happens before the indicator update at N.close.
- Sizing uses only information known at N.close.
- The N+1 open is used only for actual execution or rejection.
- Bars after `endDate` are never used.

## 6. State ownership

Single source of truth for each piece of state:

| State | Owner |
|---|---|
| Indicator state | each runtime `Indicator` |
| Strategy state | none |
| Position quantity | `Portfolio` |
| Cash | `Portfolio` |
| Cost basis | `Portfolio` |
| Realized P&L | `Portfolio` |
| Pending order | the `Backtester` run loop |
| Trading history | `List<Fill>` |
| Rejected signals | `List<OrderRejection>` |
| Trades | derived from fills after the run |
| Equity | append-only `List<EquityPoint>` |

`Backtester` itself holds no persistent fields and is therefore re-entrant.

## 7. Fill / signal / trade relationship

```
bar N close: condition true -> SignalEvent (ENTER|EXIT, IndicatorSnapshot); date = snapshot.date()
                                  |
                 +----------------+-----------------+
                 |                                  |
     qty <= 0 at sizing                    Order(id, qty, signal); side derived
     -> OrderRejection.ZeroQuantity                  |
        (no id consumed; date = signal.date())        |
                                  bar N+1 open: execute
                                                      |
                                +---------------------+-------------------+
                                |                                         |
                Fill(orderId, date, qty,                    OrderRejection.InsufficientCash
                    referenceOpen, fillPrice,                (orderId, date, qty, requiredCash,
                    commission, signal);                      availableCash, signal)
                    side/slippageCost derived                 date = execution bar's date
                                |
                     after the run: fills -> Trades
```

## 8. Indicator semantics

All V1 indicators (SMA, EMA, RSI) use close prices.

- **SMA(n)**: ready after `n` closes.
- **EMA(n)**: seeded with the SMA of the first `n` closes; then
  `alpha = 2 / (n + 1)`, `EMA = alpha × close + (1 − alpha) × EMA_prev`.
- **RSI(n)**: Wilder method; requires `n + 1` closes. Initial average
  gain/loss are simple averages of the first `n` price changes;
  subsequently `(prev × (n − 1) + current) / n`.
  Edge cases: `avgLoss = 0` and `avgGain > 0` → 100;
  `avgGain = 0` and `avgLoss = 0` → 50.

Before readiness, the strategy is not evaluated. `firstEvaluableDate`
reports the first in-range bar at which all indicators are ready.

Separate lookback: pre-start bars warm indicators but do not generate
strategy activity.

## 9. Execution semantics

- market orders only
- next available bar open
- fixed commission per fill, applied to both BUY and SELL fills
- adverse percentage slippage: BUY `open × (1 + rate)`,
  SELL `open × (1 − rate)`
- slippage cost = `|fillPrice − referenceOpen| × quantity`, computed by
  the execution step and available on `Fill` as the derived
  `slippageCost()` (D-21) — not stored, and not itself the calculation
  that produces `fillPrice`
- whole shares, long-only, no leverage
- insufficient cash: reject the whole order; no partial fill
- zero quantity: record a rejection rather than creating an order
- invalid OHLC and invalid series ordering: rejected before the run starts
- zero-volume bars execute normally in V1; there is no liquidity model yet

## 10. Portfolio accounting

State: cash, position quantity, cost basis including buy commissions,
realized P&L.

```
BUY q at fill price f with commission c:
  cash      -= q × f + c
  quantity  += q
  costBasis += q × f + c

SELL q at fill price f with commission c:
  removedBasis = costBasis × q / currentQuantity
  proceeds     = q × f − c
  cash        += proceeds
  realizedPnl += proceeds − removedBasis
  costBasis   -= removedBasis
  quantity    -= q

At a close P:
  marketValue   = quantity × P
  unrealizedPnl = quantity × P − costBasis
  equity        = cash + quantity × P
```

Average cost is derived for display and is not separately stored.

Accounting identity, tested for every in-range equity point:

```
equity = initialCapital + realizedPnl + unrealizedPnl
```

## 11. Trade derivation

Because V1 supports only one long position, full exits, and no pyramiding,
fills alternate `BUY, SELL, BUY, SELL, …`.

- Each BUY followed by a SELL produces one closed `Trade`.
- A trailing BUY produces one open `Trade`.
- Open trades are marked to the final close, are not counted as completed
  trades, and are excluded from win rate, average win and average loss.
- Closed-trade net P&L equals the portfolio's realized P&L contribution for
  that exit.
- Trade explanations come from `entry.fill.signal` and `exit.fill.signal`.

## 12. Numerical policy

| Type | Used for |
|---|---|
| `BigDecimal` | prices, fill prices, commissions, slippage rate, cash fraction, cash, cost basis, P&L, equity, initial capital |
| `long` | quantities |
| `int` | periods, per-run order IDs |
| `double` | indicator calculations, indicator outputs, condition comparisons, later derived statistics |

- Use `compareTo` for `BigDecimal` comparisons.
- Money is not rounded to cents inside the engine.
- A fixed `MathContext` is used for the one partial-basis division if
  required.
- This policy is not redesigned during the first implementation stages
  without a concrete correctness problem.

## 13. Determinism and reproducibility

Identical `BarSeries` content, `StrategyDefinition`, `BacktestConfig`, and
engine version must produce identical results.

Avoided: current time, randomness, static mutable state, parallel streams
affecting order, hash-order-dependent behavior, locale-sensitive engine
behavior. Order IDs are sequential. Indicator creation order is
deterministic.

The exact supplied lookback data matters: EMA and RSI seeding depend on
where the supplied series begins. Experiment persistence must therefore
eventually identify the exact supplied dataset/series, including
sufficient lookback provenance.

## 14. Testing strategy

Small hand-calculable fixtures, not only external market data.

- **Bar/BarSeries**: invalid price, invalid high/low, invalid volume,
  duplicate date, out-of-order date, empty series, immutability.
- **Indicators**: SMA warm-up and values, EMA warm-up and seeded values,
  RSI Wilder calculations, RSI edge cases, readiness.
- **Strategy** (implemented; `engine.strategy`): operand resolution
  (indicator, close, constant), GT/LT including equal operands (both
  false), constant-vs-constant and identical-operand comparisons (both
  permitted), AND/OR with nesting and short-circuiting, construction
  validation (empty `All`/`Any`, non-finite constant), missing spec
  propagating the snapshot's exception, structural equality, purity, and a
  reflection check that the grammar's records hold no `Bar`, `BarSeries`,
  or runtime `Indicator`. No evaluation before readiness is a `Backtester`
  concern, not tested here.
- **StrategyDefinition / PositionSizing** (implemented; `engine.strategy`,
  D-19): `CashFraction` bounds (`0 < f <= 1`) and `-0.0`-style trailing-zero
  normalization; null-component rejection; structural equality over
  `(entry, exit, sizing)`; `requiredIndicatorSpecs()` discovery — single
  and shared specs, multiple periods of one type, both `Compare` operands,
  nesting several levels deep, `Close`/`Constant` contributing nothing, no
  indicators at all, canonical ordering independent of authoring/traversal
  order, an unmodifiable result, and cross-checked against
  `IndicatorSnapshot`'s canonical order for the same specs.
- **SignalEvent / Order** (implemented; `engine.strategy` /
  `engine.execution`, D-20): null-component rejection; `date()` equals
  `snapshot.date()`; structural equality; runtime isolation (a signal's
  snapshot is unaffected by later updates to the runtime indicator that
  produced it); `id >= 1` and `quantity > 0` validation; `side()` derived
  correctly for both signal types; a reflection check that neither type
  has a component beyond its approved shape (no stored date, price,
  status, or `OrderSide`).
- **Fill / OrderRejection** (implemented; `engine.execution`, D-21): every
  stored field preserved; `side()` and `slippageCost()` derived correctly
  (including a zero-slippage case); zero commission accepted; every
  numeric/null validation boundary (`orderId >= 1`, `quantity > 0`,
  positive prices, non-negative commission); `ZeroQuantity`'s date
  equal to the signal date and its rejection of an EXIT signal;
  `InsufficientCash`'s independent execution date, its `requiredCash >
  availableCash` check (equal and lesser values both rejected), and its
  rejection of an EXIT signal; the explanation recoverable from a `Fill`
  without an `Order` object; structural equality; a whitelist reflection
  check that neither type holds an `Order`, `Portfolio`, runtime
  `Indicator`, `Bar`/`BarSeries`, or a mutable collection. Order-ID gap
  behavior and the one-outcome-per-signal invariant are documented here
  and become Backtester-level tests, not tested at this layer.
- **Execution** (Backtester-level, not yet implemented): signal at bar N close, fill at bar N+1 open, no same-bar
  fill, no signal on the last in-range bar, commission, slippage,
  insufficient-cash rejection, zero-quantity rejection.
- **Lookback/range**: pre-start bars warm indicators, pre-start bars create
  no trading activity, `endDate` inclusive, bars after `endDate` ignored,
  `endDate` on a non-trading day, series ending before `endDate`.
- **Portfolio**: buying, marking to market, selling, realized/unrealized
  P&L, accounting identity.
- **Trade**: closed trade, open trade, explanation recovered from fills.
- **Determinism**: identical input twice produces an identical result.

## 15. Engine boundary

```
BacktestResult Backtester.run(
    BarSeries series,
    StrategyDefinition strategy,
    BacktestConfig config
)
```

Input: immutable market data, immutable strategy, immutable config.
Output: immutable `BacktestResult`.

No callbacks. No I/O. No persistence. No HTTP. No Spring. Engine domain
types must never become JPA entities.