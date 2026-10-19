package com.aydog4nn.reserveflow.payment.dto.response;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentScenario scenario,
        PaymentStatus status,
        Instant readyAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
