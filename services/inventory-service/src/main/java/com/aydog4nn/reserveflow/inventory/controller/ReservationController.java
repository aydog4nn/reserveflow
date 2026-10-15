package com.aydog4nn.reserveflow.inventory.controller;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.dto.request.ReserveStockRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ReservationResponse;
import com.aydog4nn.reserveflow.inventory.mapper.ReservationMapper;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final IInventoryService inventoryService;
    private final ReservationMapper reservationMapper;

    @PostMapping("/by-order/{orderId}/commit")
    public ReservationResponse commitReservation(@PathVariable("orderId") UUID orderId) {
        return reservationMapper.toResponse(inventoryService.commitReservation(orderId));
    }

    @PostMapping("/by-order/{orderId}/release")
    public ReservationResponse releaseReservation(@PathVariable("orderId") UUID orderId) {
        return reservationMapper.toResponse(inventoryService.releaseReservation(orderId));
    }

    public ReservationController(IInventoryService inventoryService, ReservationMapper reservationMapper) {
        this.inventoryService = inventoryService;
        this.reservationMapper = reservationMapper;
    }

    @PostMapping
    public ReservationResponse reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        Reservation reservation = inventoryService.reserveStock(
                request.orderId(), request.productId(), request.quantity()
        );
        return reservationMapper.toResponse(reservation);
    }
}
