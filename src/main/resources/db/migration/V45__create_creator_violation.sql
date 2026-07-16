CREATE TABLE creator_violation (
    id          BIGSERIAL PRIMARY KEY,
    creator_id  UUID NOT NULL REFERENCES users(id),
    order_id    UUID NOT NULL UNIQUE REFERENCES orders(id),
    type        VARCHAR(30) NOT NULL,
    severity    INT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_creator_violation_creator_id ON creator_violation(creator_id);
