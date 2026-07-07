-- Creator self-service application tables

CREATE TABLE creator_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL,
    name VARCHAR(100),
    activity_type VARCHAR(20) NOT NULL,
    category_id BIGINT REFERENCES categories(id),
    other_text VARCHAR(255),
    social_type VARCHAR(20) NOT NULL,
    ig_username VARCHAR(100),
    ig_verify_code VARCHAR(40),
    ig_ownership_confirmed BOOLEAN NOT NULL DEFAULT false,
    telegram_verification_id UUID REFERENCES telegram_verification(id),
    sample_video_url VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    decision_reason VARCHAR(1000),
    reviewed_by UUID REFERENCES users(id),
    tracking_token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE application_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES creator_applications(id),
    author VARCHAR(20) NOT NULL,
    text VARCHAR(2000),
    file_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);
