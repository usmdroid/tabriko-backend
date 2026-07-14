CREATE TABLE requisite_catalog (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL,
    emoji      VARCHAR(10),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE creator_requisite (
    id               BIGSERIAL PRIMARY KEY,
    creator_user_id  UUID         NOT NULL,
    catalog_id       BIGINT       REFERENCES requisite_catalog(id),
    name             VARCHAR(60)  NOT NULL,
    emoji            VARCHAR(10),
    source           VARCHAR(10)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_creator_requisite_creator ON creator_requisite(creator_user_id);

INSERT INTO requisite_catalog (name, emoji) VALUES
    ('Gul',    '🌸'),
    ('Tort',   '🎂'),
    ('Olovcha','🎇'),
    ('Sharlar','🎈');
