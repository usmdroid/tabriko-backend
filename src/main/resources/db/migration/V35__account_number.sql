ALTER TABLE users ADD COLUMN account_number VARCHAR(12);

-- Stable backfill: derive 7-char hex suffix from MD5 of user UUID
UPDATE users SET account_number = 'TBR-' || upper(substr(md5(id::text), 1, 7));

ALTER TABLE users ALTER COLUMN account_number SET NOT NULL;
CREATE UNIQUE INDEX uq_users_account_number ON users (account_number);
