ALTER TABLE user_devices
  ADD COLUMN device_id          VARCHAR(100),
  ADD COLUMN rooted             BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN genuine            BOOLEAN,
  ADD COLUMN blocked            BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN attest_public_key  VARCHAR(2000);

CREATE UNIQUE INDEX uq_user_devices_user_device
  ON user_devices (user_id, device_id)
  WHERE device_id IS NOT NULL;

CREATE TABLE device_attest_nonces (
  nonce       VARCHAR(64) PRIMARY KEY,
  device_id   VARCHAR(100) NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  used        BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE platform_settings
  ADD COLUMN block_rooted_devices BOOLEAN NOT NULL DEFAULT FALSE;
