-- Existing grades remain private until the teacher explicitly publishes them.
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS result_published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS result_published_at TIMESTAMP;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS result_published_by UUID;
