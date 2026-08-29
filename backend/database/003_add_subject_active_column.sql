ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE subjects
SET active = TRUE
WHERE active IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS subjects_name_unique_idx
    ON subjects (LOWER(name));
