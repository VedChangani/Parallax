# Parallax

**Parallax** is a systematic investment research and historical backtesting platform.

It combines a deterministic, framework-independent Java backtesting engine with a Spring Boot backend and React frontend.

## Features

* Historical OHLCV market data via CSV and Alpha Vantage
* Rule-based strategy builder with SMA, EMA and RSI
* Deterministic historical backtesting
* Commission, slippage and portfolio accounting
* CAGR, Sharpe, volatility, drawdown and trade analytics
* Buy-and-hold benchmark comparison
* Immutable strategy, dataset and backtest versions
* User authentication and isolated research data

## Tech Stack

**Engine:** Java 21
**Backend:** Spring Boot, PostgreSQL
**Frontend:** React, Vite

## Architecture

```text
React Frontend
      ↓
Spring Boot Backend
      ↓
Java Backtesting Engine
      ↓
PostgreSQL
```

The engine is independent of Spring, JPA and PostgreSQL.

For the detailed architecture and design decisions:

* [`docs/architecture.md`](docs/architecture.md)
* [`docs/decisions.md`](docs/decisions.md)

## Running Locally

### Prerequisites

* Java 21
* Node.js
* PostgreSQL

### Backend

Create the local PostgreSQL database:

```sql
CREATE ROLE parallax WITH LOGIN PASSWORD 'parallax';
CREATE DATABASE parallax OWNER parallax;
```

Then from the repository root:

```bash
./mvnw install -DskipTests
./mvnw -pl backend spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend proxies `/api` requests to the backend.

### Authentication

Authentication is enabled locally.

To claim the existing `dev` account:

```text
PARALLAX_CLAIM_USERNAME=dev
PARALLAX_CLAIM_PASSWORD=<15+ characters>
```

New users can also register through the application's Register page.

### Alpha Vantage

To enable Alpha Vantage imports:

```text
ALPHA_VANTAGE_API_KEY=<your-key>
```

## Testing

Backend:

```bash
./mvnw verify
```

Frontend:

```bash
cd frontend
npm test
npm run lint
```
