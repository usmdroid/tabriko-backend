-- Normalize existing phone numbers to the canonical "+<digits>" format used by
-- PhoneUtil.normalize() at the application boundary (auth, admin, telegram bot).
-- Historic rows may have been stored in mixed raw formats (spaces, dashes, no "+"),
-- which caused duplicate users / failed lookups for the same real phone number.

-- users.phone is UNIQUE NOT NULL: two differently-formatted raw values could
-- normalize to the same digits and collide. Guard against that by only updating
-- rows whose normalized form has no other row already occupying it; anything
-- that would collide is left untouched and logged via RAISE NOTICE so it can be
-- resolved manually instead of breaking the migration.
DO $$
DECLARE
    r RECORD;
    normalized VARCHAR(20);
BEGIN
    FOR r IN SELECT id, phone FROM users LOOP
        normalized := '+' || regexp_replace(r.phone, '[^0-9]', '', 'g');
        IF normalized = r.phone THEN
            CONTINUE;
        END IF;
        IF EXISTS (
            SELECT 1 FROM users
            WHERE id <> r.id
              AND '+' || regexp_replace(phone, '[^0-9]', '', 'g') = normalized
        ) THEN
            RAISE NOTICE 'Skipping phone normalization for users.id=% (phone=%): would collide with another user after normalizing to %',
                r.id, r.phone, normalized;
            CONTINUE;
        END IF;
        UPDATE users SET phone = normalized WHERE id = r.id;
    END LOOP;
END $$;

-- creator_applications.phone and telegram_verification.phone have no unique
-- constraint, so a plain normalize-in-place is safe.
UPDATE creator_applications
SET phone = '+' || regexp_replace(phone, '[^0-9]', '', 'g')
WHERE phone IS NOT NULL
  AND phone <> '+' || regexp_replace(phone, '[^0-9]', '', 'g');

UPDATE telegram_verification
SET phone = '+' || regexp_replace(phone, '[^0-9]', '', 'g')
WHERE phone IS NOT NULL
  AND phone <> '+' || regexp_replace(phone, '[^0-9]', '', 'g');
