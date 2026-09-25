-- D-31: the deterministic development user SeededCurrentUser resolves until
-- real authentication exists. Its lifecycle (disabling/replacing it, never
-- deleting it outright once it owns historical resources) is a decision for
-- the later security batch — see docs/decisions.md D-31.

INSERT INTO app_user (username) VALUES ('dev');
