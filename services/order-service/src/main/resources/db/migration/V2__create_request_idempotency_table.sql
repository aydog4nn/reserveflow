CREATE TABLE request_idempotency (
    client_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_request_idempotency PRIMARY KEY (client_id, idempotency_key),
    CONSTRAINT fk_request_idempotency_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT chk_request_idempotency_client CHECK (btrim(client_id) <> ''),
    CONSTRAINT chk_request_idempotency_key CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT chk_request_idempotency_hash CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_request_idempotency_order_id ON request_idempotency (order_id);
