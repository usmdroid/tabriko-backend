ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS suspension_reason TEXT,
    ADD COLUMN IF NOT EXISTS suspended_at      TIMESTAMPTZ;
