-- Add accepting flag and options collection to creator profiles

ALTER TABLE creator_profiles ADD COLUMN accepting BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE creator_profile_options (
    creator_id  UUID        NOT NULL REFERENCES creator_profiles(user_id) ON DELETE CASCADE,
    option_name VARCHAR(30) NOT NULL,
    PRIMARY KEY (creator_id, option_name)
);

-- Seed existing demo creators with all options enabled
INSERT INTO creator_profile_options (creator_id, option_name)
SELECT cp.user_id, opt.name
FROM creator_profiles cp
CROSS JOIN (VALUES ('SHER'), ('HAZIL'), ('ANEKDOT'), ('GIVEN_TEXT'), ('SURPRISE')) AS opt(name)
WHERE cp.is_verified = true
ON CONFLICT DO NOTHING;
