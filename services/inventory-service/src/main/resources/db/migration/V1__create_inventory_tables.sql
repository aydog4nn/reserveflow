CREATE TABLE products (
                          id UUID PRIMARY KEY,
                          sku VARCHAR(64) NOT NULL,
                          name VARCHAR(255) NOT NULL,
                          unit_price NUMERIC(19, 2) NOT NULL,
                          currency VARCHAR(3) NOT NULL,
                          active BOOLEAN NOT NULL,

                          CONSTRAINT uq_products_sku UNIQUE (sku),
                          CONSTRAINT chk_products_sku_not_blank CHECK (BTRIM(sku) <> ''),
                          CONSTRAINT chk_products_name_not_blank CHECK (BTRIM(name) <> ''),
                          CONSTRAINT chk_products_price CHECK (unit_price >= 0),
                          CONSTRAINT chk_products_currency CHECK (currency ~ '^[A-Z]{3}$')
    );

CREATE TABLE stock (
                       product_id UUID PRIMARY KEY,
                       on_hand INTEGER NOT NULL DEFAULT 0,
                       reserved INTEGER NOT NULL DEFAULT 0,
                       version BIGINT NOT NULL DEFAULT 0,

                       CONSTRAINT fk_stock_product
                           FOREIGN KEY (product_id) REFERENCES products (id),

                       CONSTRAINT chk_stock_on_hand CHECK (on_hand >= 0),
                       CONSTRAINT chk_stock_reserved CHECK (reserved >= 0),
                       CONSTRAINT chk_stock_available CHECK (reserved <= on_hand)
);

CREATE TABLE reservations (
                              id UUID PRIMARY KEY,
                              order_id UUID NOT NULL,
                              product_id UUID NOT NULL,
                              quantity INTEGER NOT NULL,
                              status VARCHAR(16) NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                              updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                              CONSTRAINT uq_reservations_order UNIQUE (order_id),

                              CONSTRAINT fk_reservations_product
                                  FOREIGN KEY (product_id) REFERENCES products (id),

                              CONSTRAINT chk_reservations_quantity CHECK (quantity > 0),

                              CONSTRAINT chk_reservations_status
                                  CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED'))
);

CREATE INDEX idx_reservations_product_id
    ON reservations (product_id);
