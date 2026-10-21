package com.aydog4nn.reserveflow.inventory.messaging.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StockFinalizedEvent(
        @NotNull UUID orderId,
        @NotNull UUID reservationId,
        @NotNull Outcome outcome
) {
    public enum Outcome {
        COMMITTED,
        RELEASED
    }
}
