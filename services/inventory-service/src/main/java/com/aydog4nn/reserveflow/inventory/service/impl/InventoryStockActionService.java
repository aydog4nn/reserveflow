package com.aydog4nn.reserveflow.inventory.service.impl;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.repository.ReservationRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryStockActionService {

    private final ReservationRepository reservations;
    private final IInventoryService inventoryService;

    public InventoryStockActionService(ReservationRepository reservations, IInventoryService inventoryService) {
        this.reservations = reservations;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public Reservation commit(UUID orderId, UUID reservationId) {
        validateReservation(orderId, reservationId);
        return inventoryService.commitReservation(orderId);
    }

    @Transactional
    public Reservation release(UUID orderId, UUID reservationId) {
        validateReservation(orderId, reservationId);
        return inventoryService.releaseReservation(orderId);
    }

    private void validateReservation(UUID orderId, UUID reservationId) {
        if (orderId == null || reservationId == null) {
            throw new IllegalArgumentException("Sipariş ve rezervasyon kimliği zorunlu.");
        }
        Reservation reservation = reservations.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Rezervasyon bulunamadı."));
        if (!reservation.getId().equals(reservationId)) {
            throw new IllegalArgumentException("Rezervasyon kimliği siparişle uyuşmuyor.");
        }
    }
}
