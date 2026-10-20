package com.aydog4nn.reserveflow.inventory.messaging.dto;

import java.util.UUID;

public record StockReservedEvent(
        UUID orderId,
        UUID reservationId
) {
}
