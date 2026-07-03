-- TabrikO initial schema

CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR(20) UNIQUE NOT NULL,
    name        VARCHAR(100),
    email       VARCHAR(150),
    role        VARCHAR(20) NOT NULL DEFAULT 'CLIENT',
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    fcm_token   VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS categories (
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(100) UNIQUE NOT NULL,
    icon_url VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS creator_profiles (
    user_id        UUID PRIMARY KEY REFERENCES users(id),
    category_id    BIGINT REFERENCES categories(id),
    bio            VARCHAR(1000),
    avg_rating     NUMERIC(3,2) DEFAULT 0,
    rating_count   INT DEFAULT 0,
    price_from     NUMERIC(12,2) DEFAULT 0,
    delivery_days  INT DEFAULT 3,
    is_top         BOOLEAN DEFAULT FALSE,
    is_exclusive   BOOLEAN DEFAULT FALSE,
    is_verified    BOOLEAN DEFAULT FALSE,
    avatar_url     VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS portfolio_items (
    id          BIGSERIAL PRIMARY KEY,
    creator_id  UUID NOT NULL REFERENCES users(id),
    media_url   VARCHAR(500) NOT NULL,
    is_public   BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           UUID NOT NULL REFERENCES users(id),
    creator_id          UUID NOT NULL REFERENCES users(id),
    type                VARCHAR(10) NOT NULL,
    option              VARCHAR(20) NOT NULL,
    recipient_name      VARCHAR(100),
    recipient_occasion  VARCHAR(200),
    custom_text         VARCHAR(2000),
    is_public           BOOLEAN DEFAULT FALSE,
    price               NUMERIC(12,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deadline            TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    rejection_reason    VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS deliveries (
    id                      BIGSERIAL PRIMARY KEY,
    order_id                UUID UNIQUE NOT NULL REFERENCES orders(id),
    media_url_watermarked   VARCHAR(500) NOT NULL,
    media_url_clean         VARCHAR(500),
    watermarked             BOOLEAN NOT NULL DEFAULT TRUE,
    delivered_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL PRIMARY KEY,
    order_id    UUID UNIQUE NOT NULL REFERENCES orders(id),
    client_id   UUID NOT NULL REFERENCES users(id),
    creator_id  UUID NOT NULL REFERENCES users(id),
    stars       SMALLINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     VARCHAR(1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id),
    amount      NUMERIC(12,2) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    order_id    UUID REFERENCES orders(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id),
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    type        VARCHAR(30) NOT NULL,
    is_read     BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_orders_client    ON orders(client_id);
CREATE INDEX IF NOT EXISTS idx_orders_creator   ON orders(creator_id);
CREATE INDEX IF NOT EXISTS idx_orders_status    ON orders(status);
CREATE INDEX IF NOT EXISTS idx_notif_user       ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_wallet_user      ON wallet_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_portfolio_creator ON portfolio_items(creator_id);
