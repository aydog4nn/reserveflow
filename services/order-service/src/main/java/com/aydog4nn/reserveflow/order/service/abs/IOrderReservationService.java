package com.aydog4nn.reserveflow.order.service.abs;

import java.util.UUID;

public interface IOrderReservationService {

    void handleStockReserved(UUID orderId, UUID reservationId);
}
