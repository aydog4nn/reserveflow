package com.aydog4nn.reserveflow.inventory.controller;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.dto.request.ReserveStockRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ReservationResponse;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final IInventoryService inventoryService;

    public ReservationController(IInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ReservationResponse reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        Reservation reservation = inventoryService.reserveStock(
                request.orderId(), request.productId(), request.quantity()
        );
        return ReservationResponse.from(reservation);
    }
}
