ALTER TABLE wallet_transactions ADD COLUMN IF NOT EXISTS provider_ref VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_wallet_tx_provider_ref
    ON wallet_transactions(provider_ref)
    WHERE provider_ref IS NOT NULL;
