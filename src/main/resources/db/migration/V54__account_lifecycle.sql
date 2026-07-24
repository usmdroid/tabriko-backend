-- Archive / soft-delete lifecycle for accounts (superadmin only).
ALTER TABLE users ADD COLUMN archived_at     TIMESTAMP;
ALTER TABLE users ADD COLUMN archive_reason  VARCHAR(500);
ALTER TABLE users ADD COLUMN deleted_at      TIMESTAMP;
ALTER TABLE users ADD COLUMN deletion_reason VARCHAR(500);
ALTER TABLE users ADD COLUMN deleted_by      UUID;

-- Speeds up the "deleted accounts" tab listing.
CREATE INDEX IF NOT EXISTS idx_users_status_deleted_at ON users (status, deleted_at);
