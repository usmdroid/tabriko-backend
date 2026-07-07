CREATE TABLE occasions (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(200) NOT NULL,
  event_date DATE NOT NULL,
  recurring_yearly BOOLEAN NOT NULL DEFAULT FALSE,
  emoji VARCHAR(16),
  color VARCHAR(16),
  image_url VARCHAR(500),
  category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO occasions (title, event_date, recurring_yearly, emoji, color, sort_order) VALUES
  ('Yangi yil', '2026-01-01', TRUE, '🎄', '#2E7D32', 1),
  ('8-mart', '2026-03-08', TRUE, '🌷', '#EC407A', 2),
  ('Navro''z', '2026-03-21', TRUE, '🌱', '#66BB6A', 3),
  ('9-may', '2026-05-09', TRUE, '🎗️', '#D32F2F', 4),
  ('Mustaqillik kuni', '2026-09-01', TRUE, '🇺🇿', '#1E88E5', 5);
