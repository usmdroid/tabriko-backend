-- Per-service pricing + discounts: replaces the single CreatorProfile.priceFrom scalar
-- with one row per (creator, OrderType) so VIDEO/AUDIO can be priced and discounted independently.
CREATE TABLE creator_service (
    id                  BIGSERIAL PRIMARY KEY,
    creator_id          UUID NOT NULL REFERENCES users(id),
    type                VARCHAR(10) NOT NULL,
    price               NUMERIC(12,2) NOT NULL,
    delivery_days       INT NOT NULL DEFAULT 3,
    accepting           BOOLEAN NOT NULL DEFAULT TRUE,
    discount_type       VARCHAR(10) NOT NULL DEFAULT 'NONE',
    discount_value      NUMERIC(12,2),
    discount_starts_at  TIMESTAMPTZ,
    discount_ends_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_creator_service_creator_type UNIQUE (creator_id, type)
);

CREATE INDEX idx_creator_service_creator ON creator_service(creator_id);

-- Backfill: every existing creator gets a VIDEO row from their historical
-- priceFrom/deliveryDays/accepting, and an AUDIO row seeded the same way since
-- OrderService previously charged the same priceFrom regardless of order type.
INSERT INTO creator_service (creator_id, type, price, delivery_days, accepting)
SELECT user_id, 'VIDEO', COALESCE(price_from, 0), COALESCE(delivery_days, 3), COALESCE(accepting, TRUE)
FROM creator_profiles
ON CONFLICT (creator_id, type) DO NOTHING;

INSERT INTO creator_service (creator_id, type, price, delivery_days, accepting)
SELECT user_id, 'AUDIO', COALESCE(price_from, 0), COALESCE(delivery_days, 3), COALESCE(accepting, TRUE)
FROM creator_profiles
ON CONFLICT (creator_id, type) DO NOTHING;
