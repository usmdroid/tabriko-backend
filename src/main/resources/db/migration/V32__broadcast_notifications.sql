CREATE TABLE broadcast_notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    body         VARCHAR(1000) NOT NULL,
    target_type  VARCHAR(10) NOT NULL,
    min_version  VARCHAR(20),
    max_version  VARCHAR(20),
    platform     VARCHAR(10),
    user_count   INT NOT NULL,
    device_count INT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
