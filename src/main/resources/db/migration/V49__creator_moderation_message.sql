CREATE TABLE creator_moderation_message (
    id              BIGSERIAL PRIMARY KEY,
    creator_user_id UUID        NOT NULL REFERENCES users(id),
    author_role     VARCHAR(20) NOT NULL,
    kind            VARCHAR(20) NOT NULL,
    body            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_by_creator BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_cmm_creator_user_id ON creator_moderation_message(creator_user_id);
