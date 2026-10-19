package com.aydog4nn.reserveflow.order.client.exception;

import java.util.UUID;

public class InventoryProductNotFoundException extends RuntimeException {

    public InventoryProductNotFoundException(UUID productId) {
        super("Inventory'de ürün bulunamadı: " + productId);
    }
}
