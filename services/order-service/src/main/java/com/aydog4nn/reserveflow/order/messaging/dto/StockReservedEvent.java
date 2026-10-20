package com.aydog4nn.reserveflow.order.messaging.dto;

import java.util.UUID;

public record StockReservedEvent(
        UUID orderId,
        UUID reservationId
) {
}
