-- Stable superadmin credentials: phone +998901234567 / password 234567.
-- The V2 seed created the superadmin user without a password_hash, so there was no
-- fixed login. This sets a known bcrypt hash so the superadmin can always sign in.
UPDATE users
SET password_hash = '$2b$10$lMlS3mqV1LLYyWauQmnpJezg93e1r/eNOaDBUB2ktkKkEoP120O3a'
WHERE id = '00000000-0000-0000-0000-000000000001';
