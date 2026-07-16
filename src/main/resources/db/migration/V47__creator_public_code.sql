-- Add column (nullable so existing rows are not immediately violated)
ALTER TABLE creator_profiles
    ADD COLUMN IF NOT EXISTS public_code VARCHAR(20) UNIQUE;

-- Backfill: generate a unique 7-char base36 code for every existing creator
DO $$
DECLARE
    rec RECORD;
    chars TEXT := 'abcdefghijklmnopqrstuvwxyz0123456789';
    code TEXT;
    attempts INT;
BEGIN
    FOR rec IN SELECT user_id FROM creator_profiles WHERE public_code IS NULL LOOP
        attempts := 0;
        LOOP
            code := '';
            FOR i IN 1..7 LOOP
                code := code || substr(chars, floor(random() * length(chars) + 1)::int, 1);
            END LOOP;
            BEGIN
                UPDATE creator_profiles SET public_code = code WHERE user_id = rec.user_id;
                EXIT;
            EXCEPTION WHEN unique_violation THEN
                attempts := attempts + 1;
                IF attempts > 50 THEN RAISE EXCEPTION 'Too many collisions for user_id %', rec.user_id; END IF;
            END;
        END LOOP;
    END LOOP;
END;
$$;

-- Now that all rows are filled, add NOT NULL constraint
ALTER TABLE creator_profiles ALTER COLUMN public_code SET NOT NULL;
