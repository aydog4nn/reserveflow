package com.aydog4nn.reserveflow.order.dto.response;

import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String currency,
        PaymentScenario paymentScenario,
        OrderStatus status,
        UUID reservationId,
        UUID paymentId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
