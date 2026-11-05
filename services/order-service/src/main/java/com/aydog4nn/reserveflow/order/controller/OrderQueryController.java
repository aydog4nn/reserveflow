package com.aydog4nn.reserveflow.order.controller;

import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.service.impl.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class OrderQueryController {
    private final OrderQueryService query;

    public OrderQueryController(OrderQueryService query) {
        this.query = query;
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable UUID orderId) {
        return query.findById(orderId);
    }
}
