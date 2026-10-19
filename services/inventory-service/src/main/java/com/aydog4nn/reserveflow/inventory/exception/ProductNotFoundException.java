package com.aydog4nn.reserveflow.inventory.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID productId) {
        super("Ürün bulunamadı: " + productId);
    }
}
