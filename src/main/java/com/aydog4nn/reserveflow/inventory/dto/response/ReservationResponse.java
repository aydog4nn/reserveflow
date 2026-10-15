package com.aydog4nn.reserveflow.inventory.dto.response;

import com.aydog4nn.reserveflow.inventory.domain.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID orderId,
        UUID productId,
        int quantity,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt
) {

}
