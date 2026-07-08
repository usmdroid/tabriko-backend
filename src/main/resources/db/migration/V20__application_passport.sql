-- Collect passport identity data at application time (series: 2 letters, number: 7 digits).
-- Nullable so existing rows stay valid; new submissions are required to provide them (validated in the API layer).
ALTER TABLE creator_applications
    ADD COLUMN passport_series VARCHAR(2),
    ADD COLUMN passport_number VARCHAR(7);
