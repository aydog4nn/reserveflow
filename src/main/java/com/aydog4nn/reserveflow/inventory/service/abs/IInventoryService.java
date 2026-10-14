package com.aydog4nn.reserveflow.inventory.service.abs;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;

import java.util.UUID;

public interface IInventoryService {

    Reservation reserveStock(UUID orderId, UUID productId, int quantity);

}
