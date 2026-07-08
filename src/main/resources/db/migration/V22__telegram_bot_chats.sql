CREATE TABLE telegram_bot_chats (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id  BIGINT       NOT NULL,
    chat_id           BIGINT       NOT NULL UNIQUE,
    chat_username     VARCHAR(100),
    chat_title        VARCHAR(255),
    chat_type         VARCHAR(30),
    subscribers       INTEGER,
    owner_status      VARCHAR(20),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_telegram_bot_chats_user ON telegram_bot_chats(telegram_user_id);
