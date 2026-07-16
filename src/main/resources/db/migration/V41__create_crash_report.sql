CREATE TABLE crash_report (
  id BIGSERIAL PRIMARY KEY,
  level VARCHAR(10) NOT NULL,
  message TEXT NOT NULL,
  stack_trace TEXT,
  platform VARCHAR(50),
  app_version VARCHAR(30),
  os_version VARCHAR(50),
  device_model VARCHAR(100),
  device_id VARCHAR(100),
  screen VARCHAR(200),
  occurred_at TIMESTAMPTZ,
  user_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_crash_report_created_at ON crash_report(created_at DESC);
CREATE INDEX idx_crash_report_level ON crash_report(level);
