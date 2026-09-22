# Parallax Project Progress

## Current Milestone

V1 engine architecture approved and documented. No engine code written yet.

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

## Current Architecture

```
Parallax/
├── pom.xml                  # root Maven aggregator (packaging=pom)
├── mvnw, mvnw.cmd, .mvn/    # Maven wrapper
├── engine/                  # plain Java engine module (skeleton only)
│   ├── pom.xml
│   └── src/{main,test}/java/in/vedchangani/engine/
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

Run from `C:\Parallax` after the documentation update:

- `./mvnw clean install`: BUILD SUCCESS. Reactor: Parallax (pom), Parallax
  Engine, backend. Engine 1/1 tests pass (`EngineSmokeTest`); backend 1/1
  tests pass (`BackendApplicationTests`).
- Docs consistency check: every D-n reference resolves to a heading in
  `decisions.md`; all progress references point to `docs/progress.md`; no
  terms from the superseded draft design remain.

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
- Nothing is committed yet; the repository has no commits.

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

`Bar` + `BarSeries` + focused validation tests. Nothing else.