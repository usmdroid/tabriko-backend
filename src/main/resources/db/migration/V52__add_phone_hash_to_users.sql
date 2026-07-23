-- Add phone_hash column for privacy-preserving contact matching.
-- Phone numbers are already in canonical "+<digits>" E.164 form (normalized by V24),
-- so sha256(phone::bytea) produces the same hash that PhoneHashUtil.hash() computes
-- in the application layer. No pgcrypto extension required — sha256(bytea) is built-in
-- since PostgreSQL 11.
ALTER TABLE users ADD COLUMN phone_hash VARCHAR(64);
UPDATE users SET phone_hash = encode(sha256(phone::bytea), 'hex') WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_phone_hash ON users(phone_hash);
