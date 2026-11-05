package com.aydog4nn.reserveflow.order.service.impl;

import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.exception.OrderNotFoundException;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderQueryService {
    private final OrderRepository orders;
    private final OrderMapper mapper;

    public OrderQueryService(OrderRepository orders, OrderMapper mapper) {
        this.orders = orders;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID orderId) {
        return mapper.toResponse(orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)));
    }
}
