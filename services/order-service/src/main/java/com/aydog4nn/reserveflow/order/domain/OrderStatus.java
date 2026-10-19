package com.aydog4nn.reserveflow.order.domain;

public enum OrderStatus {
    PENDING_STOCK,
    PAYMENT_PENDING,
    STOCK_COMMIT_PENDING,
    COMPENSATION_PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
