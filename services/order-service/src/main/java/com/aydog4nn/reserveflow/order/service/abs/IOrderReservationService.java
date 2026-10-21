package com.aydog4nn.reserveflow.order.service.abs;

import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;

import java.util.UUID;

public interface IOrderReservationService {

    OrderResponse handleStockReserved(UUID orderId, UUID reservationId);
}
