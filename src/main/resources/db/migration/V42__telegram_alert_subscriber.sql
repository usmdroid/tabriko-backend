-- Chat ids that have started the crash-alert Telegram bot. Collected via
-- getUpdates polling; critical alerts are broadcast to all of them.
CREATE TABLE telegram_alert_subscriber (
    chat_id     BIGINT       PRIMARY KEY,
    username    VARCHAR(100),
    first_name  VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
