CREATE TABLE orders (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_scenario VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reservation_id UUID,
    payment_id UUID,
    failure_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    CONSTRAINT chk_orders_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_orders_total_amount CHECK (total_amount >= 0 AND total_amount = unit_price * quantity),
    CONSTRAINT chk_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_orders_payment_scenario CHECK (
        payment_scenario IN ('SUCCESS', 'DECLINE', 'DELAY', 'TECHNICAL_FAILURE')
    ),
    CONSTRAINT chk_orders_status CHECK (
        status IN ('PENDING_STOCK', 'PAYMENT_PENDING', 'STOCK_COMMIT_PENDING',
                   'COMPENSATION_PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')
    )
);

CREATE INDEX idx_orders_status_updated_at ON orders (status, updated_at);
