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
