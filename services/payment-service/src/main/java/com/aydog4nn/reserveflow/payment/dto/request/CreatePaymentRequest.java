package com.aydog4nn.reserveflow.payment.dto.request;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull(message = "Sipariş kimliği zorunlu.")
        UUID orderId,
        @NotNull(message = "Ödeme tutarı zorunlu.")
        @PositiveOrZero(message = "Ödeme tutarı negatif olamaz.")
        @Digits(integer = 17, fraction = 2, message = "Tutar en fazla 17 tam ve 2 ondalık basamak içerebilir.")
        BigDecimal amount,
        @NotNull(message = "Para birimi zorunlu.")
        @Pattern(regexp = "[A-Z]{3}", message = "Para birimi üç büyük harf olmalı.")
        String currency,
        @NotNull(message = "Ödeme senaryosu zorunlu.")
        PaymentScenario scenario
) {
}
