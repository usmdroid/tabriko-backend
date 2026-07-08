-- Admin-managed promo/announcement cards for the home screen's right-hand carousel
-- (replaces plain creator cards there — creators already appear in several sections below).
CREATE TABLE promotions (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  subtitle VARCHAR(300),
  image_url VARCHAR(500),
  color VARCHAR(16),
  category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
  external_url VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);
