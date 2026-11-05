package com.aydog4nn.reserveflow.order.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Sipariş bulunamadı: " + orderId);
    }
}
