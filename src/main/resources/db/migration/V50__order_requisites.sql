-- option is now optional in the API; drop the NOT NULL constraint
ALTER TABLE orders ALTER COLUMN option DROP NOT NULL;

-- Join table: records which requisites were selected for an order
CREATE TABLE order_requisites (
    order_id      UUID   NOT NULL REFERENCES orders(id),
    requisite_id  BIGINT NOT NULL REFERENCES creator_requisite(id),
    PRIMARY KEY (order_id, requisite_id)
);
