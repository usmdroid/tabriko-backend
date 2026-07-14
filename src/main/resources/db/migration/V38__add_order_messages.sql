CREATE TABLE order_messages (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID NOT NULL REFERENCES orders(id),
    author     VARCHAR(20) NOT NULL,
    text       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_messages_order_created ON order_messages(order_id, created_at);
