package com.aydog4nn.reserveflow.inventory.dto.response;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
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

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getOrderId(),
                reservation.getProduct().getId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }
}
