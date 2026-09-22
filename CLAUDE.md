# Parallax — Project Instructions

## Project Identity

Parallax is a systematic investment research and historical backtesting platform.

The project exists to rigorously test investment/trading strategies against historical market data under explicit, deterministic and reproducible assumptions.

This is a finance + software-engineering project.

It is NOT:

* an ML stock predictor
* an AI recommendation system
* a live brokerage
* a paper-trading application
* a generic stock dashboard
* a TradingView clone
* a Backtrader clone
* a QuantConnect clone

## Primary Goal

Build a genuinely implemented Java backtesting engine surrounded by a Spring Boot research application.

The strongest parts of the project must be:

1. backtesting correctness
2. deterministic execution
3. portfolio accounting
4. reproducibility
5. financial-domain correctness
6. clean backend architecture

Do not optimize for feature count.

## Technology

Backend/application:

* Java 21
* Spring Boot 4.1.x
* Maven
* PostgreSQL
* Spring Data JPA / Hibernate
* REST APIs

Backtesting engine:

* Plain Java
* JUnit
* No Spring dependencies

Frontend:

* React
* Vite
* JavaScript

## Repository Structure

The repository should contain:

* engine/ — framework-independent backtesting engine
* backend/ — Spring Boot application
* frontend/ — React application
* docs/ — architecture and project decisions
* docs/progress.md — current project state

## Core Architectural Rule

The backtesting engine must remain independent of Spring Boot.

The dependency direction is:

frontend
->
backend
->
engine

The engine must never depend on:

* Spring
* JPA
* PostgreSQL
* REST
* HTTP
* React

The backend orchestrates the engine and persists application data.

## V1 Scope

V1 is intentionally limited.

Support:

* daily OHLCV bars
* one instrument per backtest
* long-only positions
* no leverage
* whole-share quantities
* SMA
* EMA
* RSI
* structured strategy rules
* market orders
* next-bar-open execution
* configurable fixed commission
* configurable percentage slippage
* cash and position tracking
* realized and unrealized P&L
* equity curve
* trade history
* total return
* CAGR
* volatility
* Sharpe ratio
* maximum drawdown
* win rate
* average win
* average loss
* trade count
* trading costs
* buy-and-hold benchmark
* strategy versions
* reproducible experiments

Explicitly defer unless separately approved:

* Kafka
* Redis
* WebSockets
* microservices
* intraday/tick data
* HFT simulation
* short selling
* leverage
* options
* futures
* complex order types
* historical replay
* walk-forward testing
* parameter optimization
* machine learning
* large universe portfolio construction
* advanced portfolio optimization

## Engine Design Principles

The engine must process market data chronologically.

A strategy must never access future information.

V1 execution baseline:

signal generated using bar N close
->
order
->
execution at bar N+1 open

Do not introduce look-ahead bias.

Insufficient cash must follow an explicit deterministic rule.

Invalid or missing market data must have explicit behavior.

Duplicate bars must be handled deterministically.

Non-trading days must not be invented.

Indicator warm-up periods must be explicitly handled.

Results must be deterministic.

Given identical:

* dataset
* strategy version
* parameters
* date range
* initial capital
* execution assumptions

the engine must produce the same result.

## Financial Correctness

Never silently invent financial assumptions.

Important assumptions must be explicit in configuration and results.

Do not claim backtest results represent guaranteed future performance.

Backtest results are historical simulations.

Document the treatment of:

* commissions
* slippage
* insufficient cash
* position sizing
* execution timing
* missing data
* indicator warm-up
* benchmark calculation

## Strategy Model

Strategies must be structured data.

Do not allow arbitrary executable user code.

A strategy conceptually contains:

* indicators
* conditions
* entry rules
* exit rules
* position sizing

Keep the V1 strategy language small.

Do not build a general-purpose expression language.

## Data Architecture

Market data must be abstracted behind a provider interface.

Use local deterministic datasets for engine development and testing.

External providers must not be called during every backtest.

Normalize external data before passing it to the engine.

Do not make the engine dependent on Alpha Vantage or any other external provider.

## Experiment Reproducibility

An experiment must preserve enough information to reproduce its result.

An experiment must refer to a specific strategy version/configuration.

Never silently modify historical experiment definitions when a strategy changes.

Dataset identity/provenance must eventually be represented explicitly.

## Engineering Workflow

Before changing code:

1. inspect the existing repository
2. inspect relevant architecture/documentation
3. understand the current implementation
4. identify affected files
5. make the smallest change that satisfies the task

After changing code:

1. run relevant tests
2. run the broader test suite when practical
3. inspect failures rather than bypassing them
4. report files changed
5. report tests executed and results
6. explain important design decisions

Do not make unrelated refactors.

Do not add technologies because they look impressive.

Do not create abstractions for hypothetical future requirements.

Prefer simple designs that solve the current problem correctly.

## Testing Requirements

Tests are part of the implementation.

For engine logic, prefer focused unit tests with deterministic fixtures.

Important financial behavior must have regression tests.

Never modify or delete a test merely to make an implementation pass.

Tests must verify behavior, not merely code coverage.

## Git / State

Do not force-push.

Do not rewrite history.

Do not delete existing work without explicit instruction.

Keep docs/progress.md updated after major milestones.

Use Git checkpoints at meaningful milestones.

## Documentation

Important architectural decisions belong in docs/.

Document non-obvious financial assumptions.

Do not create excessive documentation for simple code.

## Claude Behavior

Be critical.

Before implementing a task, check whether the requested design introduces:

* unnecessary complexity
* overlap with existing functionality
* financial correctness problems
* scope risk
* architectural coupling
* reproducibility problems

When a requirement is ambiguous or potentially dangerous, inspect the repository and existing documentation before choosing an implementation.

Do not silently expand scope.

If a requested feature conflicts with the architecture, explain the conflict before implementing it.

The quality bar is a serious portfolio engineering project, not a toy demo.

## Current Development Rule

Work in small logical batches.

Complete and verify one batch before moving to the next.

Do not implement multiple major subsystems in one prompt unless explicitly requested.
