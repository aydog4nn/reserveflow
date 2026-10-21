package com.aydog4nn.reserveflow.payment.messaging.dto;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record RequestPaymentCommand(
        @NotNull UUID orderId,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull PaymentScenario scenario
) {
}
