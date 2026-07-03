-- Add portfolio consent to orders; link portfolio items to orders optionally

ALTER TABLE orders ADD COLUMN portfolio_consent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE portfolio_items ADD COLUMN order_id UUID REFERENCES orders(id);

-- Seed: sample wallet topup for demo client
INSERT INTO wallet_transactions (user_id, amount, type, status)
SELECT u.id, 500000.00, 'DEPOSIT', 'COMPLETED'
FROM users u WHERE u.role = 'CLIENT' LIMIT 1;
