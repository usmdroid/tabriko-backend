CREATE TABLE telegram_verification (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id  BIGINT       NOT NULL,
    user_id           UUID         REFERENCES users(id),
    phone             VARCHAR(20),
    chat_id           BIGINT,
    chat_username     VARCHAR(100),
    chat_title        VARCHAR(255),
    chat_type         VARCHAR(30),
    subscribers       INTEGER,
    owner_status      VARCHAR(20),
    status            VARCHAR(20)  NOT NULL,
    verified_at       TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_tgverif_telegram_user_id ON telegram_verification(telegram_user_id);
