-- D-34 Batch 1: the persistence foundation for a completed BacktestRun.
--
-- Every run is inserted once, after it has finished (no run status, no
-- Backtester invocation from this migration or the entities it supports).
-- Strategy and dataset identity are each tied to their immutable version
-- by a composite foreign key, so a persisted run can never reference a
-- version whose hash disagrees with what strategy_version/dataset_version
-- actually stores; an owner_id composite FK onto strategy/dataset further
-- guarantees the referenced strategy/dataset belong to the same owner as
-- the run itself. engine_semantics_version records which Backtester
-- chronological/execution semantics produced this result (see
-- Backtester.SEMANTICS_VERSION).
--
-- backtest_equity_point / backtest_fill / backtest_rejection are immutable
-- children of backtest_run, following D-32's dataset_bar precedent: no
-- surrogate id, a natural composite primary key, plain JDBC access (see
-- BacktestEquityPointRepository/BacktestFillRepository/BacktestRejectionRepository),
-- never a JPA entity or association.
--
-- backtest_equity_point deliberately does not store market_value/equity/
-- unrealized_pnl: those remain derived by the engine's own EquityPoint,
-- exactly as D-22 designed it. backtest_fill deliberately does not store
-- side/slippage_cost: both are derived from the engine Fill. There is no
-- signal_type column on backtest_rejection: both rejection reasons
-- (ZERO_QUANTITY, INSUFFICIENT_CASH) represent an ENTER signal only (D-21).

-- --- composite-FK targets on existing immutable version/parent tables ------

ALTER TABLE strategy
    ADD CONSTRAINT uq_strategy_id_owner UNIQUE (id, owner_id);

ALTER TABLE dataset
    ADD CONSTRAINT uq_dataset_id_owner UNIQUE (id, owner_id);

ALTER TABLE strategy_version
    ADD CONSTRAINT uq_strategy_version_strategy_number_hash
        UNIQUE (strategy_id, version_number, definition_hash);

ALTER TABLE dataset_version
    ADD CONSTRAINT uq_dataset_version_dataset_number_hash
        UNIQUE (dataset_id, version_number, content_hash);

-- --- backtest_run -----------------------------------------------------------

CREATE TABLE backtest_run (
    id                          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id                    bigint NOT NULL,
    created_at                  timestamptz NOT NULL DEFAULT now(),

    strategy_id                 bigint NOT NULL,
    strategy_version_number     integer NOT NULL,
    strategy_definition_hash    varchar(64) NOT NULL,

    dataset_id                  bigint NOT NULL,
    dataset_version_number      integer NOT NULL,
    dataset_content_hash        varchar(64) NOT NULL,

    engine_semantics_version    integer NOT NULL,

    initial_capital             numeric NOT NULL,
    commission_per_fill         numeric NOT NULL,
    slippage_rate                numeric NOT NULL,
    start_date                  date NOT NULL,
    end_date                    date NOT NULL,

    first_evaluable_date        date,
    total_commission             numeric NOT NULL,
    total_slippage_cost          numeric NOT NULL,

    total_return                 double precision NOT NULL,
    cagr                         double precision,
    volatility                   double precision,
    sharpe_ratio                 double precision,
    max_drawdown                 double precision NOT NULL,
    closed_trade_count           integer NOT NULL,
    win_rate                     double precision,
    average_win                  double precision,
    average_loss                 double precision,

    benchmark_cash               numeric NOT NULL,
    benchmark_quantity           bigint NOT NULL,
    benchmark_cost_basis         numeric NOT NULL,
    benchmark_total_return       double precision NOT NULL,

    CONSTRAINT fk_backtest_run_owner FOREIGN KEY (owner_id)
        REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_backtest_run_strategy FOREIGN KEY (strategy_id, owner_id)
        REFERENCES strategy (id, owner_id) ON DELETE RESTRICT,
    CONSTRAINT fk_backtest_run_strategy_version
        FOREIGN KEY (strategy_id, strategy_version_number, strategy_definition_hash)
        REFERENCES strategy_version (strategy_id, version_number, definition_hash) ON DELETE RESTRICT,
    CONSTRAINT fk_backtest_run_dataset FOREIGN KEY (dataset_id, owner_id)
        REFERENCES dataset (id, owner_id) ON DELETE RESTRICT,
    CONSTRAINT fk_backtest_run_dataset_version
        FOREIGN KEY (dataset_id, dataset_version_number, dataset_content_hash)
        REFERENCES dataset_version (dataset_id, version_number, content_hash) ON DELETE RESTRICT,

    CONSTRAINT ck_backtest_run_strategy_hash_format CHECK (strategy_definition_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_backtest_run_dataset_hash_format CHECK (dataset_content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_backtest_run_date_range CHECK (start_date <= end_date),
    CONSTRAINT ck_backtest_run_semantics_version CHECK (engine_semantics_version >= 1),
    CONSTRAINT ck_backtest_run_closed_trade_count CHECK (closed_trade_count >= 0),
    CONSTRAINT ck_backtest_run_benchmark_quantity CHECK (benchmark_quantity >= 0),

    -- PostgreSQL orders/equates NaN as if it were larger than every other
    -- float8 value (including +Infinity), unlike IEEE 754 — so "x = x"
    -- does not reject NaN here. "x > '-Infinity' AND x < 'Infinity'"
    -- rejects both NaN and either infinity, which is the intended check.
    CONSTRAINT ck_backtest_run_total_return_finite
        CHECK (total_return > '-Infinity' AND total_return < 'Infinity'),
    CONSTRAINT ck_backtest_run_cagr_finite
        CHECK (cagr IS NULL OR (cagr > '-Infinity' AND cagr < 'Infinity')),
    CONSTRAINT ck_backtest_run_volatility_finite
        CHECK (volatility IS NULL OR (volatility > '-Infinity' AND volatility < 'Infinity')),
    CONSTRAINT ck_backtest_run_sharpe_ratio_finite
        CHECK (sharpe_ratio IS NULL OR (sharpe_ratio > '-Infinity' AND sharpe_ratio < 'Infinity')),
    CONSTRAINT ck_backtest_run_max_drawdown_finite
        CHECK (max_drawdown > '-Infinity' AND max_drawdown < 'Infinity'),
    CONSTRAINT ck_backtest_run_win_rate_finite
        CHECK (win_rate IS NULL OR (win_rate > '-Infinity' AND win_rate < 'Infinity')),
    CONSTRAINT ck_backtest_run_average_win_finite
        CHECK (average_win IS NULL OR (average_win > '-Infinity' AND average_win < 'Infinity')),
    CONSTRAINT ck_backtest_run_average_loss_finite
        CHECK (average_loss IS NULL OR (average_loss > '-Infinity' AND average_loss < 'Infinity')),
    CONSTRAINT ck_backtest_run_benchmark_total_return_finite
        CHECK (benchmark_total_return > '-Infinity' AND benchmark_total_return < 'Infinity')
);

CREATE INDEX idx_backtest_run_owner_id ON backtest_run (owner_id, id);

-- --- backtest_equity_point ---------------------------------------------------

CREATE TABLE backtest_equity_point (
    run_id          bigint NOT NULL,
    bar_date        date NOT NULL,
    cash            numeric NOT NULL,
    quantity        bigint NOT NULL,
    cost_basis      numeric NOT NULL,
    realized_pnl    numeric NOT NULL,
    close           numeric NOT NULL,
    CONSTRAINT pk_backtest_equity_point PRIMARY KEY (run_id, bar_date),
    CONSTRAINT fk_backtest_equity_point_run FOREIGN KEY (run_id)
        REFERENCES backtest_run (id) ON DELETE RESTRICT
);

-- --- backtest_fill -----------------------------------------------------------

CREATE TABLE backtest_fill (
    run_id              bigint NOT NULL,
    order_id            integer NOT NULL,
    fill_date           date NOT NULL,
    quantity            bigint NOT NULL,
    reference_open      numeric NOT NULL,
    fill_price          numeric NOT NULL,
    commission          numeric NOT NULL,
    signal_type         varchar(16) NOT NULL,
    signal_date         date NOT NULL,
    signal_close        numeric NOT NULL,
    signal_indicators   jsonb NOT NULL,
    CONSTRAINT pk_backtest_fill PRIMARY KEY (run_id, order_id),
    CONSTRAINT uq_backtest_fill_date UNIQUE (run_id, fill_date),
    CONSTRAINT fk_backtest_fill_run FOREIGN KEY (run_id)
        REFERENCES backtest_run (id) ON DELETE RESTRICT,
    CONSTRAINT ck_backtest_fill_signal_type CHECK (signal_type IN ('ENTER', 'EXIT')),
    CONSTRAINT ck_backtest_fill_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_backtest_fill_signal_indicators_array CHECK (jsonb_typeof(signal_indicators) = 'array')
);

-- --- backtest_rejection -------------------------------------------------------

CREATE TABLE backtest_rejection (
    run_id              bigint NOT NULL,
    seq                 integer NOT NULL,
    reason              varchar(32) NOT NULL,
    order_id            integer,
    execution_date      date,
    quantity            bigint,
    required_cash       numeric,
    available_cash      numeric,
    signal_date         date NOT NULL,
    signal_close        numeric NOT NULL,
    signal_indicators   jsonb NOT NULL,
    CONSTRAINT pk_backtest_rejection PRIMARY KEY (run_id, seq),
    CONSTRAINT fk_backtest_rejection_run FOREIGN KEY (run_id)
        REFERENCES backtest_run (id) ON DELETE RESTRICT,
    CONSTRAINT ck_backtest_rejection_reason CHECK (reason IN ('ZERO_QUANTITY', 'INSUFFICIENT_CASH')),
    CONSTRAINT ck_backtest_rejection_signal_indicators_array CHECK (jsonb_typeof(signal_indicators) = 'array'),
    -- ZERO_QUANTITY: no Order was ever created, so no order-derived field
    -- exists. INSUFFICIENT_CASH: an Order existed and all four fields are
    -- always known. See engine OrderRejection (D-21).
    CONSTRAINT ck_backtest_rejection_shape CHECK (
        (reason = 'ZERO_QUANTITY'
            AND order_id IS NULL AND execution_date IS NULL AND quantity IS NULL
            AND required_cash IS NULL AND available_cash IS NULL)
        OR
        (reason = 'INSUFFICIENT_CASH'
            AND order_id IS NOT NULL AND execution_date IS NOT NULL AND quantity IS NOT NULL
            AND required_cash IS NOT NULL AND available_cash IS NOT NULL)
    )
);

-- --- immutability: no update, no delete, no truncate ------------------------
--
-- One shared trigger function for all four new tables (mirroring D-32's
-- reject_dataset_snapshot_modification, which is likewise reused across
-- dataset_version and dataset_bar) — not a new generic trigger framework,
-- just the same established pattern applied within this migration.

CREATE FUNCTION reject_backtest_run_modification() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is immutable: % is not permitted', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_backtest_run_immutable_row
    BEFORE UPDATE OR DELETE ON backtest_run
    FOR EACH ROW EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_run_immutable_truncate
    BEFORE TRUNCATE ON backtest_run
    FOR EACH STATEMENT EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_equity_point_immutable_row
    BEFORE UPDATE OR DELETE ON backtest_equity_point
    FOR EACH ROW EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_equity_point_immutable_truncate
    BEFORE TRUNCATE ON backtest_equity_point
    FOR EACH STATEMENT EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_fill_immutable_row
    BEFORE UPDATE OR DELETE ON backtest_fill
    FOR EACH ROW EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_fill_immutable_truncate
    BEFORE TRUNCATE ON backtest_fill
    FOR EACH STATEMENT EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_rejection_immutable_row
    BEFORE UPDATE OR DELETE ON backtest_rejection
    FOR EACH ROW EXECUTE FUNCTION reject_backtest_run_modification();

CREATE TRIGGER trg_backtest_rejection_immutable_truncate
    BEFORE TRUNCATE ON backtest_rejection
    FOR EACH STATEMENT EXECUTE FUNCTION reject_backtest_run_modification();
