ALTER TABLE creator_profiles
    ADD COLUMN tier               VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN id_document_number VARCHAR(255),
    ADD COLUMN id_document_url    VARCHAR(500),
    ADD COLUMN payout_card        VARCHAR(255),
    ADD COLUMN payout_account     VARCHAR(255),
    ADD COLUMN payout_holder      VARCHAR(255),
    ADD COLUMN social_telegram    VARCHAR(255),
    ADD COLUMN social_instagram   VARCHAR(255),
    ADD COLUMN profile_complete   BOOLEAN      NOT NULL DEFAULT false;
