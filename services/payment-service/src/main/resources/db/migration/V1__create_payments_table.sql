CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    scenario VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    ready_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_payments_order_id UNIQUE (order_id),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0),
    CONSTRAINT chk_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payments_scenario CHECK (
        scenario IN ('SUCCESS', 'DECLINE', 'DELAY', 'TECHNICAL_FAILURE')
    ),
    CONSTRAINT chk_payments_status CHECK (
        status IN ('PENDING', 'SUCCEEDED', 'FAILED')
    )
);

CREATE INDEX idx_payments_status_ready_at ON payments (status, ready_at);
