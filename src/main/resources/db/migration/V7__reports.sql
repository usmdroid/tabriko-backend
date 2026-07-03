-- Reports table for user/content/order complaints

CREATE TABLE reports (
    id          BIGSERIAL   PRIMARY KEY,
    reporter_id UUID        NOT NULL REFERENCES users(id),
    target_type VARCHAR(20) NOT NULL,
    target_id   VARCHAR(100) NOT NULL,
    reason      TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reports_reporter ON reports(reporter_id);
CREATE INDEX idx_reports_status   ON reports(status);
