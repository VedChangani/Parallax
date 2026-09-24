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

# Engine Architecture (V1 — approved design, implemented)

Status: approved design, implemented — see "Implementation status" below.
Decisions referenced as D-n are in [decisions.md](decisions.md).

## Implementation status

Implemented (all packages below `engine`, plus `Backtester` at the engine
root):

- `Backtester`: the chronological run loop (`run(BarSeries,
  StrategyDefinition, BacktestConfig) -> BacktestResult`), D-25
- `data`: `Bar`, `BarSeries`
- `indicator`: `IndicatorType`, `IndicatorSpec`, `Indicator` (including
  its `Indicator.create(IndicatorSpec)` factory method),
  `SimpleMovingAverage`, `ExponentialMovingAverage`,
  `RelativeStrengthIndex`, `IndicatorSnapshot`
- `strategy`: `Operand`, `Operator`, `Condition`, `PositionSizing`,
  `StrategyDefinition`, `SignalType`, `SignalEvent`
- `execution`: `OrderSide`, `Order`, `Fill`, `RejectionReason`,
  `OrderRejection`
- `portfolio`: `Portfolio`, `EquityPoint`
- `result`: `BacktestConfig`, `Trade`, `BacktestResult`
- `metrics`: `PerformanceMetrics` (D-26), `BuyAndHoldBenchmark` (D-28)

The V1 engine is now feature-complete for its scope: `Backtester.run(...)`
is a working chronological simulation, `PerformanceMetrics.of(...)`
computes the full V1 metric set from its result, `BacktestResult` exposes
exact trading-cost totals (D-27), and `BuyAndHoldBenchmark.of(...)`
computes the independent passive benchmark (D-28). Not yet implemented:
backend integration and frontend.

This section reflects current state only; see Git history for how it was
reached.

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
PerformanceMetrics.of(result)      [implemented, D-26]

BuyAndHoldBenchmark.of(series, result)      [implemented, D-28 — independent of Backtester/StrategyDefinition]
```

- `Backtester` is stateless. It holds no persistent fields and is
  re-entrant. Each backtest creates its own mutable runtime state.
- Processing is single-threaded and single-pass over an immutable,
  pre-validated `BarSeries`.
- Metrics are separate post-processing logic. They are not calculated
  inside the chronological loop.

Package layout (all implemented):

```
in.vedchangani.parallax.engine             Backtester
in.vedchangani.parallax.engine.data        Bar, BarSeries
in.vedchangani.parallax.engine.indicator   IndicatorType, IndicatorSpec, Indicator, IndicatorSnapshot
in.vedchangani.parallax.engine.strategy    StrategyDefinition, Condition, Operand, Operator, PositionSizing, SignalType, SignalEvent
in.vedchangani.parallax.engine.execution   Order, OrderSide, Fill, RejectionReason, OrderRejection
in.vedchangani.parallax.engine.portfolio   Portfolio, EquityPoint
in.vedchangani.parallax.engine.result      BacktestConfig, BacktestResult, Trade
in.vedchangani.parallax.engine.metrics     PerformanceMetrics, BuyAndHoldBenchmark
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

`static Indicator Indicator.create(IndicatorSpec spec)` is the sole
mapping from an `IndicatorSpec` definition to a fresh runtime instance —
an exhaustive `switch` on `spec.type()` with no `default` branch (`SMA` →
`SimpleMovingAverage`, `EMA` → `ExponentialMovingAverage`, `RSI` →
`RelativeStrengthIndex`), so a new `IndicatorType` fails to compile here
until handled. Every call returns a new, independent instance; there is
no registry, no factory class, and no static state.

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

### Portfolio and EquityPoint (package `portfolio`, see D-22)

```
final class Portfolio                              // mutable, per run, never shared
  BigDecimal cash; long quantity; BigDecimal costBasis; BigDecimal realizedPnl
  Portfolio(BigDecimal initialCash)                 // >= 0; flat, basis 0, realized 0
  void apply(Fill fill)                             // the ONLY mutator; all-or-nothing
  EquityPoint markToMarket(LocalDate date, BigDecimal close)   // pure — does not mutate
  cash(); quantity(); costBasis(); realizedPnl(); isFlat()

record EquityPoint(LocalDate date, BigDecimal cash, long quantity,
                   BigDecimal costBasis, BigDecimal realizedPnl, BigDecimal close)
  BigDecimal marketValue()   -> close * quantity                     // derived
  BigDecimal equity()        -> cash + marketValue()                 // derived
  BigDecimal unrealizedPnl() -> marketValue() - costBasis             // derived
```

`Portfolio` is the engine's first mutable type and the single source of
truth for financial state: cash, position quantity, total cost basis, and
cumulative realized P&L — nothing else. It stores no last price, no
unrealized P&L, no equity, and no average cost; average cost is
deliberately not computed anywhere in the engine (D-13/D-22) — it would
be the engine's only division, and it is display-only, so it is left to a
later reporting layer. There is no `Position` object, lots, or FIFO/LIFO:
one quantity and one cost basis fully describe V1's single position. The
Backtester holds no cash or position copy of its own; it only calls
`Portfolio`.

V1 is long-only with no pyramiding (D-16), so `apply(Fill)` enforces a
strict flat/long state machine: a BUY is accepted only while flat, and a
SELL only for exactly the full held quantity. Every precondition is
checked before any field changes, so a rejected `apply` leaves the
portfolio completely unchanged:

- **BUY**: rejected (`IllegalStateException`) if not flat, or if
  `quantity × fillPrice + commission > cash` (an execution/Backtester
  bug, since execution should already have produced an
  `InsufficientCash` rejection). Otherwise: `cash -= totalCost`,
  `quantity = fill.quantity()`, `costBasis = totalCost` — the BUY
  commission is included in the basis.
- **SELL**: rejected if flat, or if `fill.quantity() != quantity` (this
  alone blocks partial exits, over-selling, and negative positions), or
  if the resulting cash would go negative (the fail-fast guard for OQ2 —
  V1 has no SELL-rejection type, so `BacktestConfig` validation must make
  this unreachable). Otherwise: `proceeds = quantity × fillPrice −
  commission`, `cash += proceeds`, `realizedPnl += proceeds − costBasis`,
  `quantity = 0`, `costBasis = 0`.

Because V1 exits the full position every time, D-13's `removedBasis =
costBasis × q / currentQuantity` always reduces to `costBasis` exactly —
**the reserved partial-basis `MathContext` (D-14) is unused in V1**, and
nothing in `Portfolio`/`EquityPoint` divides or rounds.

`markToMarket(date, close)` validates its arguments (null → NPE,
non-positive close → IAE) and returns a new `EquityPoint` built from the
current four fields plus `date`/`close`. It never mutates `Portfolio`,
and the current close is never stored — repeated calls at different
closes leave `cash`/`quantity`/`costBasis`/`realizedPnl` unchanged.

`EquityPoint` is an immutable observation, not a log entry: it carries
the four portfolio facts plus `date` and `close`, and derives
`marketValue()`, `equity()` and `unrealizedPnl()` by exact arithmetic
rather than storing them — a stored equity could disagree with `cash +
close × quantity`. Keeping `costBasis` and `realizedPnl` on every point
is what makes D-13's accounting identity checkable from the equity curve
alone. It holds no `Order`, `Fill`, runtime `Indicator`, `BarSeries`, or
`StrategyDefinition`. Validation mirrors `Portfolio`'s own invariants:
`cash >= 0`, `quantity >= 0`, `close > 0`, and `quantity == 0` if and only
if `costBasis == 0` (with `costBasis > 0` required when long). Equality
is default record equality, scale-sensitive like `Fill` (D-21) — ledger
values are never normalized.

There is no `PortfolioState` type. The architecture's earlier sketch of
one would have exactly duplicated the last `EquityPoint`'s fields; the
result's final state is simply that last point.

### BacktestConfig, Trade and BacktestResult (package `result`, see D-24)

```
record BacktestConfig(BigDecimal initialCapital, BigDecimal commissionPerFill,
                      BigDecimal slippageRate, LocalDate startDate, LocalDate endDate)
  initialCapital > 0; commissionPerFill >= 0; 0 <= slippageRate < 1; startDate <= endDate
  every BigDecimal canonicalized (stripTrailingZeros, scale clamped to >= 0) — exact, no rounding

sealed interface Trade { Fill entry(); default long quantity() { return entry().quantity(); } }
  record Open(Fill entry)                 // entry BUY; position still held at run end
  record Closed(Fill entry, Fill exit)    // entry BUY, exit SELL, same quantity, exit after entry
    BigDecimal realizedPnl()        -> (q*f_exit - c_exit) - (q*f_entry + c_entry)   // derived, matches Portfolio exactly
    BigDecimal totalCommission()    -> c_entry + c_exit                              // derived
    BigDecimal totalSlippageCost()  -> entry.slippageCost() + exit.slippageCost()    // derived
  static List<Trade> fromFills(List<Fill> fills)   // parses an ordered fill sequence; does not sort/repair

record BacktestResult(String symbol, StrategyDefinition strategy, BacktestConfig config,
                      Optional<LocalDate> firstEvaluableDate,
                      List<EquityPoint> equityCurve, List<Fill> fills, List<OrderRejection> rejections)
  List<Trade> trades()      -> Trade.fromFills(fills)      // derived, not stored
  EquityPoint finalPoint()  -> equityCurve.getLast()        // derived; no PortfolioState type
  BigDecimal totalCommission()    -> Σ fill.commission() over all fills     // derived, D-27
  BigDecimal totalSlippageCost()  -> Σ fill.slippageCost() over all fills   // derived, D-27
```

**BacktestConfig** is the immutable input to one run.
`initialCapital` must be strictly positive — a zero-capital run could
never trade, and it is the denominator for every future return
calculation; `Portfolio` itself still allows a zero starting balance as a
runtime value (D-22), so this is a stricter requirement on what a
meaningful backtest *request* looks like, not a change to `Portfolio`.
`commissionPerFill` is a fixed monetary amount charged once per `Fill`,
identical for BUY and SELL — never a percentage or a per-share charge.
`slippageRate` is the adverse fraction applied to a bar's open
(`BUY -> open×(1+rate)`, `SELL -> open×(1−rate)`); it must be strictly
less than 1 so a SELL fill price is always positive for any valid
(positive) open — see D-23 for why this bound alone is not sufficient to
guarantee SELL cash-safety. Every `BigDecimal` field is canonicalized at
construction (following the `CashFraction` precedent, D-19) so equal
configurations — `10000` and `10000.00`, or `0.001` and `0.0010` — are
equal values. `startDate`/`endDate` remain the existing inclusive range;
same-day ranges are valid; there is still no time-of-day or timezone
concept and no lookback-count field.

**Trade** is derived after the run from the executed fills, never from
signals or Portfolio state directly. A sealed interface with `Open` and
`Closed` was chosen over one record with a nullable exit field: the
distinction is then explicit and exhaustively matchable, with no
ambiguous null. `Open` holds only its entry `Fill`; it is never
force-liquidated (D-8), and its mark-to-market is the run's final
`EquityPoint`, not anything stored on the trade. `Closed` holds only its
entry and exit fills and derives everything else — `realizedPnl()` is
built from exactly the two expressions `Portfolio` itself computes
(`costBasis` at the BUY, `proceeds − costBasis` at the SELL), so the sum
of every closed trade's realized P&L equals `Portfolio`'s final
`realizedPnl` exactly. There is no trade ID: `entry().orderId()` already
identifies a trade uniquely, since one entry order produces at most one
fill. `Trade.fromFills` is a parser of an already-chronological,
already-valid fill sequence (BUY, SELL, BUY, SELL, ... optionally
trailing an unpaired BUY) — it never sorts, repairs, or silently skips a
malformed sequence.

**BacktestResult** is the complete immutable output. It keeps `symbol`,
`strategy` and `config` — not the supplied `BarSeries` — so the result
explains what was run without duplicating the dataset; dataset
identity/provenance remains a backend concern (D-15). `firstEvaluableDate`
is an `Optional<LocalDate>`: empty is a valid historical fact (the
required indicators never became ready inside the range), not an error,
so there is no sentinel or nullable field. `trades()` and `finalPoint()`
are derived on every call rather than stored, so there is no duplicate
state to keep in sync with `fills`/`equityCurve`; construction validates
that both lists are internally consistent (ascending dates,
BUY/SELL-alternating fills starting with BUY), so `trades()` can never
throw for a successfully constructed result. There is no `PortfolioState`
type — `finalPoint()` already is the final state (D-22). The two cost
totals are likewise derived, not stored (D-27 — see §12). Every list is
defensively copied (`List.copyOf`) and exposed as unmodifiable.
`equityCurve` must be non-empty (a valid run always has at least one
in-range bar); `fills` and `rejections` may be empty. No `Portfolio`,
runtime `Indicator`, pending `Order`, or `Backtester` reference is
reachable from a `BacktestResult`.

Ordering is chronological and deterministic throughout: one `EquityPoint`
per in-range bar in ascending date order; `fills` in execution order
(strictly ascending dates, since V1 has at most one fill per bar); and
`rejections` in the order they were appended within the run — an
`InsufficientCash` from a bar's open phase before a `ZeroQuantity` from
that same bar's close phase, when both occur on the same date. No
timestamp is introduced; `LocalDate` plus execution phase is enough.

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
`OrderRejection` (covered by Backtester tests instead); a `Position`
class, lots, or FIFO/LIFO cost-basis tracking; pyramiding / weighted-
average cost basis (V1 enforces strict flat↔long instead); partial SELLs;
a computed or stored `averageCost` anywhere in the engine; a stored last
price, unrealized P&L, or equity on `Portfolio`; a mutating
`markToMarket`; stored `marketValue`/`equity`/`unrealizedPnl` on
`EquityPoint`; a `PortfolioState` type (it would duplicate the last
`EquityPoint`); a `Portfolio` interface or service layer; the Backtester
keeping its own cash/position copy alongside `Portfolio`'s; a SELL
rejection type to solve OQ2 (the position would become permanently
unexitable — D-23 rejects this in favor of an exit-commission reserve at
entry); dataset-aware preflight validation of `BacktestConfig` against a
specific `BarSeries` (path-dependent and complex; the exit-commission
reserve makes it unnecessary); a nullable exit field on a single `Trade`
record (the sealed `Open`/`Closed` split is explicit instead); a `Trade`
ID (`entry().orderId()` is already sufficient); storing `Trade`'s derived
P&L/commission/slippage figures; a `PortfolioState`-shaped duplicate field
on `BacktestResult`; storing the supplied `BarSeries` on `BacktestResult`
(only `symbol` is kept; D-15); a nullable/sentinel `firstEvaluableDate`
(an `Optional` instead); storing `trades()` on `BacktestResult`; limit orders; stop
orders; order book; broker model; event bus;
partial strategy exits; multiple simultaneous positions;
metrics inside the loop.

The provider abstraction belongs to the backend. The engine receives a
validated `BarSeries`.

### Backtester (engine root, see D-25)

```
public final class Backtester           // no fields — stateless, re-entrant
  BacktestResult run(BarSeries, StrategyDefinition, BacktestConfig)
    -> new Run(series, strategy, config).execute()

  static long enterQuantity(cash, fraction, close, commission, slippageRate)
    // pure D-23 sizing arithmetic, package-private, independently testable

  private static final class Run        // owns every mutable per-run value; discarded after execute()
    Portfolio; Map<IndicatorSpec, Indicator> (LinkedHashMap, canonical order);
    Order pendingOrder; int nextOrderId = 1; Optional<LocalDate> firstEvaluableDate;
    List<EquityPoint>; List<Fill>; List<OrderRejection>
```

`Backtester` itself is stateless — every run creates its own `Run`, and
nothing mutable escapes it; only the immutable values copied into the
returned `BacktestResult` do. `enterQuantity` is a small pure static
method rather than a class, since D-23's arithmetic has no state of its
own and benefits from being unit-tested directly. There is no service
layer, broker model, or execution framework — `Run` executes orders
inline, which keeps the loop in one place, readable top to bottom (D-5).
The exact bar-by-bar algorithm this runs is §3.

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
           requiredCash = quantity × fillPrice + commission + commission
                          (entry commission + one reserved exit commission — D-23)
           if requiredCash > available cash: reject the entire order
           (OrderRejection.InsufficientCash, carrying the order's id).
           Do NOT reduce quantity. The reserved second commission is
           NOT charged: Portfolio.apply(BUY) still deducts only
           quantity × fillPrice + commission (D-22 unchanged); the
           reserve exists only in this affordability check.
     SELL: fillPrice = open × (1 − slippageRate)
           sell the full held position.
           (D-23 guarantees this can never leave cash negative, given a
           valid BacktestConfig — see decisions.md D-23 for the proof.)

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
     spendable = min(cash × fraction, cash − commission)   (D-23: reserve
                 one commission in cash for the future exit)
     quantity  = floor( (spendable − commission)
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
  indicators are ready, represented as `Optional<LocalDate>` on
  `BacktestResult` (D-24) — empty when they never become ready in range,
  which is a valid historical fact and not an error. With no required
  indicators it is the first in-range bar.

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

`Backtester` itself holds no persistent fields and is therefore
re-entrant. It never keeps its own copy of cash or position — every read
goes through `Portfolio`'s accessors, and the only mutation path is
`Portfolio.apply(Fill)` (D-22).

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
- exit-commission reserve (D-23): sizing an ENTER reserves one
  `commissionPerFill` in cash for the eventual exit, and BUY
  affordability checks for that reserve on top of the entry commission —
  this closes OQ2 (a SELL can now never leave cash negative given a
  valid `BacktestConfig`); the reserve is never itself charged to the BUY
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

Average cost is not computed anywhere in the engine (D-22): it is
display-only, and would be the engine's only division. A later reporting
layer derives it from `costBasis / quantity` if it wants to show it.

V1 has no partial exits (D-16): every SELL uses the full held quantity,
so `removedBasis` always equals the entire `costBasis`, `quantity` always
becomes exactly `0`, and `costBasis` always becomes exactly `0`. This is
why `Portfolio` implements SELL without a division: `q == currentQuantity`
always holds by construction (`apply` rejects any other quantity), so
`removedBasis = costBasis`.

`Portfolio.apply(Fill)` additionally enforces, before changing any state:
a BUY only while flat and only if `q × f + c <= cash`; a SELL only while
long, only for `q == currentQuantity`, and only if the resulting cash
would stay `>= 0`. Any violation throws `IllegalStateException` and
leaves the portfolio unchanged — see "Portfolio and EquityPoint" in §2.

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
- Implemented as `Trade.fromFills(List<Fill>)` (D-24): a sealed
  `Trade.Open`/`Trade.Closed` parsed from the result's `fills`, called
  fresh by `BacktestResult.trades()` rather than stored.

## 12. Performance metrics

Implemented: `engine.metrics.PerformanceMetrics` (D-26), a pure post-run
value computed by `PerformanceMetrics.of(BacktestResult)`. It participates
in none of signal generation, sizing, execution, portfolio mutation, or
chronological processing (D-5) — it consumes an already-produced immutable
`BacktestResult` and never mutates it or anything reachable from it.

```
public record PerformanceMetrics(
    double totalReturn,
    OptionalDouble cagr,
    OptionalDouble volatility,
    OptionalDouble sharpeRatio,
    double maxDrawdown,
    int closedTradeCount,
    OptionalDouble winRate,
    OptionalDouble averageWin,
    OptionalDouble averageLoss
)
```

Every metric that can be undefined for a given result is an
`OptionalDouble` with exactly one documented emptiness condition — never
`NaN`, `Infinity`, or a sentinel value.

**Preconditions of `of(result)`** (IAE if violated, NPE for a null
result): the first equity point's equity must equal
`config.initialCapital()` exactly, and every equity point's equity must be
strictly positive. Both always hold for genuine `Backtester` output (no
fill can occur before the second in-range bar, and cash cannot go negative
— D-23), so these checks exist to keep the formulas below mutually
consistent rather than to reject real runs.

**Total return:** `(finalEquity − initialCapital) / initialCapital`. An
open final position counts at its mark-to-market value on the last close
(D-8, no forced liquidation); a no-trade run is exactly `0.0`.

**CAGR (ACT/365 Fixed):** `StrictMath.pow(finalEquity/initialCapital,
365/days) − 1`, where `days` is the span between the first and last
equity-point dates. **Empty when that span is under 365 days** — V1 never
annualizes a sub-year return (GIPS convention), so even a run over a full
calendar year (e.g. 360 observed days) can have no CAGR while still
reporting total return.

**Periodic returns:** simple arithmetic returns between consecutive equity
points only — `n` points give `n − 1` returns. `initialCapital` is never
added as a separate observation (the first equity point already equals
it). A data gap between two consecutive equity points is still exactly one
return observation; V1 does not calendar-gap adjust it.

**Volatility:** sample standard deviation (divisor `n − 1`) of the
periodic returns, annualized by a fixed `× √252` (assumes daily bars).
Empty for fewer than two returns. If every return is bitwise-equal, the
standard deviation is defined as exactly `0.0` — without this rule, mean
subtraction over identical doubles leaves ~1e-17 of spurious dispersion.

**Sharpe ratio:** `mean(returns) / stdDev × √252`, with the risk-free rate
fixed at zero (no config field — V1 has no rate series or period-
conversion convention). Empty for fewer than two returns or zero
volatility (including a flat no-trade run, where the ratio is undefined).
Never `NaN`/`Infinity`.

**Maximum drawdown:** the largest close-to-close fall from a running peak
equity, as a fraction of that peak (`0.25` = 25%). Always present, `0.0`
for a single point or monotonically rising equity. No absolute monetary
amount, drawdown series, duration, or peak/trough dates are stored.

**Trade statistics:** derived only from `result.trades()`, using
`Trade.Closed.realizedPnl()` directly — never recomputed. Only closed
trades count (`closedTradeCount`); an open trade is never a win or a loss.
A trade's P&L above zero is a win, below zero a loss, exactly zero is
breakeven — a breakeven trade is in the win-rate denominator but excluded
from both averages. `winRate` is empty iff `closedTradeCount == 0`.
`averageWin`/`averageLoss` are empty when there are no wins/losses
respectively; `averageLoss` is reported as a negative number.

**Numerical policy:** every metric is a `double` (D-14). Monetary
differences and sums are performed exactly in `BigDecimal`; conversion to
`double` happens only at each metric's own statistical calculation
boundary. `StrictMath.pow`/`StrictMath.sqrt` are used throughout, never
`Math.pow`/`Math.sqrt`, for bit-reproducible results across platforms
(D-15). No `MathContext`, no rounding to cents.

**Out of V1 scope** (deferred, not implemented): non-zero risk-free rate,
Sortino/Calmar/beta/alpha/VaR, and drawdown duration/dates. Trading-cost
totals and the buy-and-hold benchmark, originally deferred here, are
implemented by D-27 and D-28 (below).

### Trading-cost totals (D-27)

Implemented as two derived methods on `BacktestResult` — not components,
not stored, and not on `PerformanceMetrics` (they are exact ledger sums,
not `double` statistics):

- `totalCommission()` = `Σ fill.commission()` over **all** fills.
- `totalSlippageCost()` = `Σ fill.slippageCost()` over **all** fills.

Both include the entry fill of a final open trade (its costs were actually
paid); neither includes a hypothetical exit cost for a still-open position
(D-8). Both are `BigDecimal.ZERO` when there are no fills, computed by
exact `BigDecimal` addition only. Summing `Trade.Closed` totals instead
would miss the open trade's entry fill.

The two are different kinds of cost. Commission is actual cash paid and
reconciles exactly with cash:

```
finalPoint().cash() = initialCapital − Σ_BUY(q × fillPrice) + Σ_SELL(q × fillPrice) − totalCommission()
```

Slippage cost is the implicit adverse-fill cost relative to each execution
bar's open. It is already embedded in the fill prices, so it is **not** an
additional cash flow and must never be subtracted from cash again. For
that reason there is no combined "total trading cost" figure.

### Buy-and-hold benchmark (D-28)

Implemented as `engine.metrics.BuyAndHoldBenchmark` — a record, not a
simulation:

```
public record BuyAndHoldBenchmark(BigDecimal initialCapital, List<EquityPoint> equityCurve)

public static BuyAndHoldBenchmark of(BarSeries series, BacktestResult result)
public double totalReturn()
```

It answers *"what would the same starting capital have produced by
passively holding the asset over the requested backtest period?"* — computed
directly from `series` and `result`'s config/equity-curve dates, **never**
by calling `Backtester.run`, evaluating a `StrategyDefinition`, or using
`Portfolio`, `Order`, or `Fill`. This corrects the D-27 `BuyAndHold`, which
delegated to `Backtester` and so inherited the strategy's gap-up rejection
behaviour (it could fail to invest at all on a steadily rising series) —
withdrawn before commit, see decisions.md open question 6.

**Entry — the first in-range bar's open (D-28's option A):**
`fillPrice = firstInRangeBar.open × (1 + slippageRate)`; whole shares
`q = floor((initialCapital − commission) / fillPrice)` via
`divideToIntegralValue`/`longValueExact`, exact, no `MathContext`. If
`initialCapital − commission ≤ 0` or `q` would be `0`, there is no trade
and no commission is charged; otherwise one commission is paid and
`costBasis = q×fillPrice + commission`. The BUY is never rejected — it is
sized from the price actually paid. The benchmark never sells: no exit
commission or slippage, no D-23 reserve, and residual cash earns nothing.
Slippage is represented only through the changed fill price, never as a
separate cash flow.

A passive holder has no decision to make and so no decision delay, unlike
a strategy sized at the prior close (D-7). Entering at the first in-range
bar's open — rather than the second bar's open (the strategy's earliest
possible fill) or the first bar's close — means both a strategy and this
benchmark hold exactly `initialCapital` at that same moment (a strategy
has no position or pending order before it, D-6) and both end at the same
last in-range close, so the two are compared over the same window and
capital, without importing the strategy's timing or rejection risk.

**Equity curve:** one `EquityPoint` per in-range bar, with dates identical
to `result.equityCurve()`'s. Because entry happens before the first mark,
every point shares the same `cash`, `quantity` and `costBasis`, with
`realizedPnl = 0` — only `date` and `close` vary. The first point is
marked at the first in-range bar's close, so its equity generally differs
from `initialCapital` (unlike a strategy's first point under D-26) — this
is why `initialCapital` is its own record component rather than read off
the curve. `EquityPoint` is reused unmodified; each point is exactly what
`Portfolio` would produce after the same BUY, marked to that close.

**Input contract, enforced not assumed:** `of` reads only
`result.symbol()`, `config()` and `equityCurve()` — never `strategy()`,
`fills()`, `rejections()`, `trades()`, or `firstEvaluableDate()` — so two
different strategies over the same series and config give an identical
benchmark. `series.symbol()` must equal `result.symbol()`, and the
series' in-range bars (by `result.config()`'s date range) must match
`result.equityCurve()` exactly in count, date and close, each an
`IllegalArgumentException` on mismatch; this cannot detect a series
differing only in bar opens or lookback content, a documented limit.

**Record validation:** `initialCapital > 0`; a non-empty,
strictly-ascending, defensively copied curve; every point sharing the same
cash/quantity/costBasis; every `realizedPnl == 0`; and
`cash + costBasis == initialCapital` exactly at every point. The first
point's equity is **not** required to equal `initialCapital` (unlike D-26's
`PerformanceMetrics` precondition).

`totalReturn()` uses the D-26 formula: an exact `BigDecimal` subtraction of
final equity minus `initialCapital`, converted to `double` only at that
statistical boundary. Benchmark CAGR, volatility, Sharpe and drawdown are
out of scope — extending `PerformanceMetrics` to a bare equity curve is a
separate decision, not made here.

## 13. Numerical policy

| Type | Used for |
|---|---|
| `BigDecimal` | prices, fill prices, commissions, slippage rate, cash fraction, cash, cost basis, P&L, equity, initial capital |
| `long` | quantities |
| `int` | periods, per-run order IDs |
| `double` | indicator calculations, indicator outputs, condition comparisons, `PerformanceMetrics` statistics (D-26) |

- Use `compareTo` for `BigDecimal` comparisons.
- Money is not rounded to cents inside the engine.
- The partial-basis `MathContext` this policy once reserved is **unused
  in V1** (D-22): V1 has no partial exits, so `removedBasis` always
  equals the full `costBasis` exactly, with no division. `Portfolio` and
  `EquityPoint` perform no division and no rounding anywhere.
- Average cost is not computed in the engine (D-13/D-22) — it is the one
  ledger quantity that would require division, and it is a display
  concern for a later reporting layer.
- This policy is not redesigned during the first implementation stages
  without a concrete correctness problem.

## 14. Determinism and reproducibility

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

## 15. Testing strategy

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
- **Portfolio / EquityPoint** (implemented; `engine.portfolio`, D-22):
  initial state (flat, `cash == initialCapital`); BUY accounting including
  the commission-in-basis figure at three different marked closes;
  winning and losing full exits; zero-commission and non-zero-commission
  round trips (commission counted exactly once, neither omitted nor
  doubled); multiple round trips accumulating realized P&L; the
  `equity == cash + marketValue` and `equity == initialCapital +
  realizedPnl + unrealizedPnl` identities checked after every state
  transition; every rejected operation (BUY while long, SELL while flat,
  partial SELL, over-selling, unaffordable BUY, cash-negative SELL)
  leaving every field provably unchanged; `markToMarket` never mutating
  across repeated calls at different closes; every null/numeric
  validation boundary on both types; `EquityPoint`'s flat-basis and
  long-basis consistency checks; a reflection check that `Portfolio`'s
  declared fields are exactly its four owned values with no static state,
  and that `EquityPoint` stores no derived value.
- **BacktestConfig / Trade / BacktestResult** (implemented;
  `engine.result`, D-24): every `BacktestConfig` bound at its edges
  (smallest positive capital, zero commission, slippage at 0 and just
  below 1, a same-day range) and just outside them; canonical-scale
  equality (`10000` = `10000.00`, `0.001` = `0.0010`); `Trade.Closed`'s
  `realizedPnl()`/`totalCommission()`/`totalSlippageCost()` matching the
  same winning/losing/round-trip figures as `PortfolioTest`; every
  `Trade` construction invariant (wrong side, mismatched quantity, exit
  not after entry, exit order id not greater); `Trade.fromFills` over
  empty/single-BUY/BUY-SELL/BUY-SELL-BUY sequences and its rejection of
  SELL-first or BUY-BUY sequences, without sorting or repairing;
  `BacktestResult`'s list immutability and defensive copying, its
  ascending/in-range equity-date and alternating-fill validation, its
  derived `trades()` and `finalPoint()` including for a trailing open
  trade, and an empty vs. present `firstEvaluableDate`; a whitelist
  reflection check that none of these types holds a `Portfolio`, `Order`,
  `BarSeries`, or runtime `Indicator`.
- **Backtester / execution** (implemented; `engine.Backtester`, D-25):
  signal at bar N close, fill at bar N+1 open, no same-bar fill, no
  signal on the last in-range bar (both that a prior pending order still
  executes there and that no new signal/order/rejection originates
  there); commission and slippage in fills; the exact affordability
  boundary (`requiredCash == availableCash` fills,
  `requiredCash > availableCash` rejects); the D-23 exit-commission
  reserve (cash left at exactly the commission after an accepted BUY, the
  reserve never charged twice, cash-safety verified at an extreme low
  exit price); zero-quantity and insufficient-cash rejections; order-ID
  sequencing across a rejection; SMA/EMA/RSI warm-up and
  `firstEvaluableDate` (including readiness reached during lookback, and
  readiness that is never reached); paired-dataset proof that sizing
  depends only on the signal bar's close, never the next bar's open; the
  full accounting/trade-reconciliation identities on a multi-trade run;
  determinism; and a structural check that `Backtester` is stateless and
  leaks no mutable runtime object into `BacktestResult`.
- **Lookback/range**: pre-start bars warm indicators, pre-start bars create
  no trading activity, `endDate` inclusive, bars after `endDate` ignored,
  `endDate` on a non-trading day, series ending before `endDate`.
- **Determinism**: identical input twice produces an identical result.
- **PerformanceMetrics** (implemented; `engine.metrics`, D-26): hand-
  calculable fixtures for total return (gain/loss/flat), CAGR (exactly one
  year, multi-year gain/loss, under-365-days empty, same-day empty),
  volatility/Sharpe (known mean/stdDev pairs, exact zero-dispersion,
  fewer-than-two-returns empty), maximum drawdown (rising, one drawdown
  with recovery, multiple drawdowns, immediate loss, single point), trade
  statistics (no trades, one win/loss/breakeven, mixed set, a trailing
  open trade excluded from closed statistics, an open-only run); every
  record-validation boundary; the two `of(result)` preconditions (first-
  equity mismatch, a zero-equity point) and a null result; determinism on
  the same and on identical results; and consistency checks against a real
  multi-trade `Backtester.run(...)` result (closed count matches
  `trades()`, closed + open counts sum to `trades().size()`, Σ closed
  `realizedPnl()` equals `finalPoint().realizedPnl()`). A structural test
  pins the record's nine components in order and confirms no component
  holds a `BacktestResult`, `Portfolio`, `BarSeries`, runtime `Indicator`,
  or `Backtester`.
- **Trading-cost totals** (implemented; `BacktestResult`, D-27): exact
  `totalCommission()`/`totalSlippageCost()` for no fills (zero), a BUY
  only (the open entry's costs counted), BUY+SELL, and BUY+SELL+BUY;
  zero slippage when fill prices equal reference opens; reconciliation
  with `Trade.Closed` totals plus the trailing open entry; and, on a real
  multi-trade `Backtester` run with non-zero commission and slippage, the
  exact cash identity and `totalCommission == commissionPerFill ×
  fills().size()`.
- **BuyAndHoldBenchmark** (implemented; `engine.metrics`, D-28): the
  normal case (exact fill price, quantity, cash, cost basis, every equity
  point, and total return with non-zero commission and slippage); the
  regression case for the withdrawn design (a steadily rising series that
  actually invests); gap-up and gap-down entries (sized from the actual
  fill price, never rejected); a one-in-range-bar window (buy at open,
  mark at close); zero quantity from too little capital, and separately
  from commission at or above capital (no trade, no commission charged);
  zero commission and zero slippage; lookback bars and bars after
  `endDate` ignored; `startDate`/`endDate` on non-trading days; identical
  results for two different `StrategyDefinition`s on the same series and
  config; alignment failures (a different symbol, a missing or extra
  in-range date, a differing close) each rejected with
  `IllegalArgumentException`; every record-validation boundary (an empty
  curve, non-ascending dates, a point disagreeing with the others on
  cash/quantity/cost-basis, non-zero `realizedPnl`, `cash + costBasis ≠
  initialCapital`, non-positive `initialCapital`); the accounting
  identities `cash + costBasis == initialCapital` and
  `equity == initialCapital + unrealizedPnl` at every point; determinism;
  and a test-only cross-check against `Portfolio`/`Fill` accounting (never
  used by the production implementation). A structural test pins the
  record's two components and confirms no component holds a
  `BacktestResult`, `BarSeries`, `StrategyDefinition`, `Portfolio`, or
  `Backtester`.

## 16. Engine boundary

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

## 17. Future direction

High-level path only — no speculative detailed design for unapproved
subsystems:

```
engine foundation (data -> indicator -> strategy -> execution -> portfolio -> result)
        |
        v
Backtester (the chronological run loop, §3) — implemented (D-25)
        |
        v
Performance metrics (CAGR, Sharpe, drawdown, win rate, ... — CLAUDE.md V1 scope; post-run, per D-5) — implemented (D-26)
        |
        v
Trading-cost totals — implemented (D-27)
        |
        v
Buy-and-hold benchmark — implemented (D-28)
        |
        v
Backend integration (Spring Boot: persistence, REST, provider integration)
        |
        v
Frontend (React: presenting strategies, experiments, results)
```

Each step is designed in its own checkpoint when work on it begins, not
in advance.