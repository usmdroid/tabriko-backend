-- Moves OTP codes and verified-phone tracking out of in-memory ConcurrentHashMaps
-- (SecureOtpService, ApplicationService) into the DB, so state survives restarts
-- and is shared correctly across multiple app instances.

CREATE TABLE IF NOT EXISTS otp_codes (
    id          BIGSERIAL PRIMARY KEY,
    phone       VARCHAR(20) NOT NULL UNIQUE,
    code        VARCHAR(10) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS verified_phones (
    id              BIGSERIAL PRIMARY KEY,
    phone           VARCHAR(20) NOT NULL UNIQUE,
    verify_token    VARCHAR(100) NOT NULL,
    ig_verify_code  VARCHAR(20),
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
