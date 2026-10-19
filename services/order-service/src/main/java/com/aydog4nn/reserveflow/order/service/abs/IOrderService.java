package com.aydog4nn.reserveflow.order.service.abs;


import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;

public interface IOrderService {


    public OrderResponse createOrder(
            CreateOrderRequest request,
            String clientId,
            String idempotencyKey
    );


}
