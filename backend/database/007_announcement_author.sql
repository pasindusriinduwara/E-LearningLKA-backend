-- Legacy global announcements intentionally keep a null author and require an administrator for deletion.
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS created_by UUID;
