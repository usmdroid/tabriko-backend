CREATE TABLE IF NOT EXISTS creator_contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone       VARCHAR(20) NOT NULL,
    label       VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_creator_contacts_creator_phone UNIQUE (creator_id, phone)
);

CREATE INDEX IF NOT EXISTS idx_creator_contacts_creator_id ON creator_contacts(creator_id);
