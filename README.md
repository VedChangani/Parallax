# Parallax

A systematic investment research and historical backtesting platform: a
framework-independent Java backtesting engine (`engine/`), a Spring Boot
research application built around it (`backend/`), and a React frontend
(`frontend/`). See [`docs/architecture.md`](docs/architecture.md) for the
full design and [`docs/decisions.md`](docs/decisions.md) for the
architectural decision register. Project rules and V1 scope are in
[`CLAUDE.md`](CLAUDE.md).

## Prerequisites

- Java 21
- Node.js (for the frontend)
- PostgreSQL (for running the backend locally)
- Docker (only needed to run the backend's integration tests, which use
  Testcontainers)

## Running the backend locally

1. Start a local PostgreSQL and create the database/role the backend
   expects by default (or point it at your own via the environment
   variables below):

   ```sql
   CREATE ROLE parallax WITH LOGIN PASSWORD 'parallax';
   CREATE DATABASE parallax OWNER parallax;
   ```

2. Start the backend from the repository root:

   ```
   ./mvnw -pl backend -am spring-boot:run
   ```

   Flyway migrates the schema automatically on startup.

3. **Authentication is always on** — there is no way to skip it, even
   locally. Every account created before Phase 9 (in particular the
   seeded `dev` account, and any account your database already has) has
   no password and cannot log in until you claim it: set both of these
   environment variables the first time you start the backend, then log
   in as `dev` with that password from the frontend:

   ```
   PARALLAX_CLAIM_USERNAME=dev
   PARALLAX_CLAIM_PASSWORD=<a password at least 15 characters long>
   ```

   This only ever sets a password on an *existing, still-passwordless*
   account — it never creates a user and never overwrites an existing
   password. Unset `PARALLAX_CLAIM_PASSWORD` again afterward. Alternatively,
   self-registration (`POST /api/auth/register`, or the frontend's
   "Register" page) is enabled by default, so a fresh install can also
   just be used by registering a new account instead of claiming `dev`.

### Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `PARALLAX_DB_URL` | `jdbc:postgresql://localhost:5432/parallax` | Datasource URL |
| `PARALLAX_DB_USERNAME` | `parallax` | Datasource username |
| `PARALLAX_DB_PASSWORD` | `parallax` | Datasource password |
| `PARALLAX_CLAIM_USERNAME` / `PARALLAX_CLAIM_PASSWORD` | unset | One-time password claim for an existing passwordless account (see above) |
| `PARALLAX_REGISTRATION_ENABLED` | `true` | Set to `false` to disable `POST /api/auth/register` |
| `PARALLAX_COOKIE_SECURE` | `true` | Set to `false` only for local `http://localhost` development without HTTPS |
| `ALPHA_VANTAGE_API_KEY` | unset (blank) | Enables Alpha Vantage dataset imports; a blank key never fails startup, only an import request |

None of these need to be set to start the backend against the database
in step 1 — only the claim variables are needed once, to log in as `dev`.

## Running the frontend locally

```
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api/*` requests to the backend on
`localhost:8080` (see `frontend/vite.config.js`), so both must be running
for the app to work end to end.

## Running the tests

```
./mvnw verify            # engine + backend, from the repository root
cd frontend && npm test  # frontend
```

The backend's integration tests (`*IT.java`) run against a real
PostgreSQL container via Testcontainers and require Docker; the engine
and other backend unit tests (`*Test.java`) do not.
