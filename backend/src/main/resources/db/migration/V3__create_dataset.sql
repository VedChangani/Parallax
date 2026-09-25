-- D-32: the first persistent market-data layer.
--
-- dataset: a stable identity owned by exactly one app_user. Only its
-- version counter mutates; name and symbol are fixed at creation (D-32 has
-- no PATCH endpoint). latest_version_number starts at 0 — a dataset may
-- exist with no versions, since dataset creation (JSON) and version
-- creation (CSV upload) are separate requests.
--
-- dataset_version: immutable. version_number is 1..n with no gaps per
-- dataset, enforced by the application's locking algorithm (mirroring
-- D-31's strategy_version) and backstopped by uq_dataset_version_number.
-- Its own symbol column is a snapshot, tied to the parent by a composite
-- FK so a version's symbol can never disagree with its dataset's. The
-- content_hash is computed from the normalized BarSeries, never raw CSV
-- bytes (see DatasetContent).
--
-- dataset_bar: one immutable row per (dataset_version_id, bar_date), exact
-- OHLCV. No surrogate id — the natural key is the primary key. A future
-- backtest run will reference dataset_version, never dataset.

CREATE TABLE dataset (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id               bigint NOT NULL,
    name                   varchar(100) NOT NULL,
    symbol                 varchar(32) NOT NULL,
    latest_version_number  integer NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_dataset_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT uq_dataset_owner_name UNIQUE (owner_id, name),
    -- Target of dataset_version's composite FK below — lets a version's
    -- symbol snapshot be verified against its parent at the database level.
    CONSTRAINT uq_dataset_id_symbol UNIQUE (id, symbol),
    CONSTRAINT ck_dataset_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_dataset_symbol_format CHECK (symbol ~ '^[A-Z0-9][A-Z0-9._-]{0,31}$'),
    CONSTRAINT ck_dataset_latest_version_non_negative CHECK (latest_version_number >= 0)
);

CREATE TABLE dataset_version (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_id          bigint NOT NULL,
    version_number      integer NOT NULL,
    symbol              varchar(32) NOT NULL,
    source              varchar(32) NOT NULL,
    source_detail       varchar(255) NOT NULL,
    adjustment_basis    varchar(32) NOT NULL,
    bar_count           integer NOT NULL,
    first_date          date NOT NULL,
    last_date           date NOT NULL,
    content_hash        varchar(64) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_dataset_version_dataset FOREIGN KEY (dataset_id, symbol)
        REFERENCES dataset (id, symbol) ON DELETE RESTRICT,
    CONSTRAINT uq_dataset_version_number UNIQUE (dataset_id, version_number),
    CONSTRAINT ck_dataset_version_number_positive CHECK (version_number >= 1),
    CONSTRAINT ck_dataset_version_source CHECK (source IN ('CSV_UPLOAD')),
    CONSTRAINT ck_dataset_version_source_detail_not_blank CHECK (btrim(source_detail) <> ''),
    CONSTRAINT ck_dataset_version_adjustment_basis CHECK (
        adjustment_basis IN ('RAW', 'SPLIT_ADJUSTED', 'SPLIT_AND_DIVIDEND_ADJUSTED')
    ),
    CONSTRAINT ck_dataset_version_bar_count_positive CHECK (bar_count >= 1),
    CONSTRAINT ck_dataset_version_date_range CHECK (first_date <= last_date),
    CONSTRAINT ck_dataset_version_hash_format CHECK (content_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE dataset_bar (
    dataset_version_id  bigint NOT NULL,
    bar_date            date NOT NULL,
    open                numeric NOT NULL,
    high                numeric NOT NULL,
    low                 numeric NOT NULL,
    close               numeric NOT NULL,
    volume              bigint NOT NULL,
    CONSTRAINT pk_dataset_bar PRIMARY KEY (dataset_version_id, bar_date),
    CONSTRAINT fk_dataset_bar_version FOREIGN KEY (dataset_version_id)
        REFERENCES dataset_version (id) ON DELETE RESTRICT
);

-- Immutability is enforced at every layer (mirroring D-31's
-- strategy_version): database triggers here; Hibernate @Immutable plus
-- updatable=false on every DatasetVersion column; no entity setters; no
-- repository/REST update-or-delete path. dataset_bar is never a JPA entity
-- at all (D-32) — its repository exposes only insertAll and an owner-scoped
-- select, so there is no update/delete code path to omit.
CREATE FUNCTION reject_dataset_snapshot_modification() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION '% is immutable: % is not permitted', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_dataset_version_immutable_row
    BEFORE UPDATE OR DELETE ON dataset_version
    FOR EACH ROW EXECUTE FUNCTION reject_dataset_snapshot_modification();

CREATE TRIGGER trg_dataset_version_immutable_truncate
    BEFORE TRUNCATE ON dataset_version
    FOR EACH STATEMENT EXECUTE FUNCTION reject_dataset_snapshot_modification();

CREATE TRIGGER trg_dataset_bar_immutable_row
    BEFORE UPDATE OR DELETE ON dataset_bar
    FOR EACH ROW EXECUTE FUNCTION reject_dataset_snapshot_modification();

CREATE TRIGGER trg_dataset_bar_immutable_truncate
    BEFORE TRUNCATE ON dataset_bar
    FOR EACH STATEMENT EXECUTE FUNCTION reject_dataset_snapshot_modification();
