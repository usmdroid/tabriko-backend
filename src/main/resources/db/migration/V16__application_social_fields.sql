-- Instagram verify code becomes a full phrase (was a short numeric code) -> widen column.
ALTER TABLE creator_applications ALTER COLUMN ig_verify_code TYPE VARCHAR(255);

-- Telegram channel/group username, entered by the applicant for reference (verification
-- itself still happens via the bot conversation, not this field).
ALTER TABLE creator_applications ADD COLUMN telegram_username VARCHAR(100);
