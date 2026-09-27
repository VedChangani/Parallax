-- D-31: the first persistent, user-owned resources.
--
-- app_user: the minimum V1 user model. No password_hash yet — real
-- authentication is a later batch (see docs/decisions.md D-31).
--
-- strategy: mutable metadata (name, description, latest_version_number),
-- owned by exactly one app_user. No delete endpoint exists, and nothing
-- deletes a strategy row from this migration onward.
--
-- strategy_version: immutable. version_number is 1..n with no gaps per
-- strategy, enforced by the application's locking algorithm and backstopped
-- by uq_strategy_version_number. The canonical D-30 definition is stored as
-- jsonb alongside its schema version and SHA-256 hash; ck_strategy_version_document
-- ties definition_schema_version to the document's own embedded schemaVersion
-- property, consistent with D-30's rule that schemaVersion is inside the
-- hashed bytes. A run will later reference strategy_version, never strategy.

CREATE TABLE app_user (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username    varchar(64) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_username_not_blank CHECK (btrim(username) <> '')
);

CREATE TABLE strategy (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id               bigint NOT NULL,
    name                   varchar(100) NOT NULL,
    description            varchar(2000) NOT NULL,
    latest_version_number  integer NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_strategy_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT uq_strategy_owner_name UNIQUE (owner_id, name),
    CONSTRAINT ck_strategy_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_strategy_latest_version_positive CHECK (latest_version_number >= 1)
);

CREATE TABLE strategy_version (
    id                         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_id                bigint NOT NULL,
    version_number             integer NOT NULL,
    definition                 jsonb NOT NULL,
    definition_schema_version  integer NOT NULL,
    definition_hash            varchar(64) NOT NULL,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_strategy_version_strategy FOREIGN KEY (strategy_id) REFERENCES strategy (id) ON DELETE RESTRICT,
    CONSTRAINT uq_strategy_version_number UNIQUE (strategy_id, version_number),
    CONSTRAINT ck_strategy_version_number_positive CHECK (version_number >= 1),
    CONSTRAINT ck_strategy_version_hash_format CHECK (definition_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_strategy_version_document CHECK (
        jsonb_typeof(definition) = 'object'
        AND (definition -> 'schemaVersion') = to_jsonb(definition_schema_version)
    )
);

-- Immutability is enforced at every layer (D-31 §12), not merely by omitting
-- application code paths. These triggers are the database-level backstop:
-- Hibernate's own @Immutable/updatable=false, no entity setters, and no
-- repository/REST update-or-delete path are the other three layers.
CREATE FUNCTION reject_strategy_version_modification() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'strategy_version is immutable: % is not permitted', TG_OP;
END;
$$;

CREATE TRIGGER trg_strategy_version_immutable_row
    BEFORE UPDATE OR DELETE ON strategy_version
    FOR EACH ROW EXECUTE FUNCTION reject_strategy_version_modification();

CREATE TRIGGER trg_strategy_version_immutable_truncate
    BEFORE TRUNCATE ON strategy_version
    FOR EACH STATEMENT EXECUTE FUNCTION reject_strategy_version_modification();
