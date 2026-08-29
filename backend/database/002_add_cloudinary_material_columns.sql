-- Run once against an existing PostgreSQL database when schema auto-update is disabled.
-- The application also creates these changes automatically with ddl-auto=update.

BEGIN;

ALTER TABLE IF EXISTS public.learning_materials
    ADD COLUMN IF NOT EXISTS cloudinary_public_id character varying(255);

ALTER TABLE IF EXISTS public.learning_materials
    ALTER COLUMN file_url TYPE character varying(1000);

COMMIT;
