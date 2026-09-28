-- D-37: adds the one column real authentication needs. Nullable —
-- password_hash IS NULL is the single "this account cannot authenticate"
-- state, covering both the pre-existing seeded `dev` row (never deleted,
-- per D-31) and any account an operator disables later by clearing the
-- column directly. There is no separate `enabled` flag.
--
-- The CHECK enforces Spring Security's DelegatingPasswordEncoder storage
-- format ("{id}encodedHash", e.g. "{bcrypt}$2a$10$..."), so a plaintext or
-- malformed value can never be written by any code path, including a
-- future migration or manual `UPDATE`.

ALTER TABLE app_user
    ADD COLUMN password_hash varchar(255) NULL,
    ADD CONSTRAINT ck_app_user_password_hash_format
        CHECK (password_hash IS NULL OR password_hash ~ '^\{[a-z0-9]+\}.+$');
