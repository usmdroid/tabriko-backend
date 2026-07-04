-- Singleton platform settings row (id is always 1)
CREATE TABLE platform_settings (
    id                 INTEGER PRIMARY KEY DEFAULT 1,
    orders_open        BOOLEAN NOT NULL DEFAULT TRUE,
    maintenance_mode   BOOLEAN NOT NULL DEFAULT FALSE,
    registration_open  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO platform_settings (id, orders_open, maintenance_mode, registration_open)
VALUES (1, true, false, true);
