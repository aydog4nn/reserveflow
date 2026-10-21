package com.aydog4nn.reserveflow.payment.messaging.dto;

import java.math.BigDecimal;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentResultEvent(
        @NotNull UUID orderId,
        @NotNull UUID paymentId,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Outcome outcome,
        @Size(max = 500) String failureReason
) {
    public enum Outcome {
        SUCCEEDED,
        FAILED
    }
}
