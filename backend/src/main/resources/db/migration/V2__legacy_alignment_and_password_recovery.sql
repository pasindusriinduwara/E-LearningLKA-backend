-- Idempotent compatibility changes preserve existing data.
-- 002_add_cloudinary_material_columns.sql
-- Run once against an existing PostgreSQL database when schema auto-update is disabled.
-- The application also creates these changes automatically with ddl-auto=update.

ALTER TABLE IF EXISTS public.learning_materials
    ADD COLUMN IF NOT EXISTS cloudinary_public_id character varying(255);

ALTER TABLE IF EXISTS public.learning_materials
    ALTER COLUMN file_url TYPE character varying(1000);



-- 003_add_subject_active_column.sql
ALTER TABLE subjects
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE subjects
SET active = TRUE
WHERE active IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS subjects_name_unique_idx
    ON subjects (LOWER(name));


-- 004_assessment_file_ownership.sql
-- Used by the local assessment upload fallback. Apply when Hibernate DDL updates are disabled.
CREATE TABLE IF NOT EXISTS assessment_files (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    filename VARCHAR(255) NOT NULL UNIQUE,
    uploaded_by UUID NOT NULL,
    assessment_id UUID,
    answer_sheet BOOLEAN NOT NULL DEFAULT FALSE
);


-- 005_submission_result_publication.sql
-- Existing grades remain private until the teacher explicitly publishes them.
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS result_published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS result_published_at TIMESTAMP;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS result_published_by UUID;


-- 006_server_timed_quiz_attempts.sql
-- Existing submissions are preserved. New quiz submissions require an explicit server-started attempt.
CREATE TABLE IF NOT EXISTS quiz_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    assessment_id UUID NOT NULL,
    student_id UUID NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_quiz_attempt_student UNIQUE (assessment_id, student_id)
);


-- 007_announcement_author.sql
-- Legacy global announcements intentionally keep a null author and require an administrator for deletion.
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS created_by UUID;


-- 008_notifications.sql
-- Durable per-user notification inbox. No historical notifications are backfilled.
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    link VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created ON notifications(recipient_id, created_at, id);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read ON notifications(recipient_id, read_at);


ALTER TABLE users ADD COLUMN IF NOT EXISTS credential_version INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  credential_version INTEGER NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset_tokens(user_id);
-- Older ERD-created databases require a subject code that the application no longer maps.
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name='subjects' AND column_name='code') THEN
    ALTER TABLE subjects ALTER COLUMN code DROP NOT NULL;
  END IF;
END $$;
