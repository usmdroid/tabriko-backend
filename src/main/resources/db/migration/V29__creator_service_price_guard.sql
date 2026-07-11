-- Defensive floor: fix any rows where V27 backfill inserted price=0
-- (COALESCE(price_from, 0) when price_from was NULL or 0).
-- Must run before the CHECK constraint or the ALTER will fail.
UPDATE creator_service SET price = 1 WHERE price <= 0;

-- Prevent future inserts/updates from writing price <= 0.
ALTER TABLE creator_service ADD CONSTRAINT creator_service_price_positive CHECK (price > 0);
