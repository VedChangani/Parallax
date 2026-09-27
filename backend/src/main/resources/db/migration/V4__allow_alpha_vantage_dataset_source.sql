-- D-33 Batch 3: Alpha Vantage becomes a second dataset_version source.
-- Widens the source CHECK constraint only; no other schema change.

ALTER TABLE dataset_version DROP CONSTRAINT ck_dataset_version_source;

ALTER TABLE dataset_version
    ADD CONSTRAINT ck_dataset_version_source CHECK (source IN ('CSV_UPLOAD', 'ALPHA_VANTAGE'));
