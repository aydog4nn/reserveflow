package com.aydog4nn.reserveflow.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReserveStockRequest(
        @NotNull(message = "Sipariş kimliği zorunlu.")
        UUID orderId,
        @NotNull(message = "Ürün kimliği zorunlu.")
        UUID productId,
        @NotNull(message = "Rezervasyon miktarı zorunlu.")
        @Positive(message = "Rezervasyon miktarı pozitif olmalı.")
        Integer quantity
) {
}
