package com.aydog4nn.reserveflow.inventory.messaging.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StockActionCommand(
        @NotNull UUID orderId,
        @NotNull UUID reservationId
) {
}
