-- Add password_hash column to users (nullable so existing/admin-created users are not forced to have a password yet)
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
