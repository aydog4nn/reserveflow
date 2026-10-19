package com.aydog4nn.reserveflow.order.controller;

import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.service.abs.IOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final IOrderService orderService;
    private final String clientId;

    public OrderController(
            IOrderService orderService,
            @Value("${order.client-id:local-lab}") String clientId) {
        if (clientId == null || clientId.isBlank() || clientId.length() > 64) {
            throw new IllegalArgumentException("Lab istemci kimliği 1-64 karakter olmalı.");
        }
        this.orderService = orderService;
        this.clientId = clientId;
    }

    @PostMapping
    public OrderResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        return orderService.createOrder(request, clientId, idempotencyKey);
    }
}
