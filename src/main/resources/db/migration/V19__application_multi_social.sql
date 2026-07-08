-- A creator application can now target BOTH Telegram and Instagram (was a single choice).
-- Store the selected networks as a list; keep the existing per-network columns
-- (ig_username / telegram_username / verification) untouched.
CREATE TABLE application_social_types (
    application_id UUID NOT NULL REFERENCES creator_applications(id) ON DELETE CASCADE,
    social_type VARCHAR(20) NOT NULL
);
CREATE INDEX idx_app_social_types_app ON application_social_types(application_id);

-- Backfill the previous single choice into the list.
INSERT INTO application_social_types (application_id, social_type)
SELECT id, social_type FROM creator_applications WHERE social_type IS NOT NULL;

-- The single-choice column is replaced by the list above.
ALTER TABLE creator_applications DROP COLUMN social_type;
