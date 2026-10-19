package com.aydog4nn.reserveflow.order.dto.request;

import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "Ürün kimliği zorunlu.")
        UUID productId,
        @NotNull(message = "Sipariş miktarı zorunlu.")
        @Positive(message = "Sipariş miktarı pozitif olmalı.")
        Integer quantity,
        @NotNull(message = "Ödeme senaryosu zorunlu.")
        PaymentScenario paymentScenario
) {
}
