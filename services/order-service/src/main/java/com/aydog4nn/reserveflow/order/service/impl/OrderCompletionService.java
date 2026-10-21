package com.aydog4nn.reserveflow.order.service.impl;

import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.messaging.dto.StockFinalizedEvent;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCompletionService {

    private final OrderRepository orders;
    private final OrderMapper mapper;
    private final Validator validator;

    public OrderCompletionService(OrderRepository orders, OrderMapper mapper, Validator validator) {
        this.orders = orders;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional
    public OrderResponse handleStockFinalized(StockFinalizedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Stok işlem sonucu zorunlu.");
        }
        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        var order = orders.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı."));
        if (!event.reservationId().equals(order.getReservationId()) || order.getPaymentId() == null) {
            throw new IllegalArgumentException("Stok sonucu siparişin rezervasyon/ödeme kaydıyla uyuşmuyor.");
        }
        boolean committed = event.outcome() == StockFinalizedEvent.Outcome.COMMITTED;
        OrderStatus expected = committed ? OrderStatus.STOCK_COMMIT_PENDING : OrderStatus.COMPENSATION_PENDING;
        OrderStatus terminal = committed ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
        if (order.getStatus() == terminal) {
            return mapper.toResponse(order);
        }
        if (order.getStatus() != expected) {
            throw new IllegalArgumentException("Sipariş bu stok işlem sonucunu beklemiyor.");
        }
        order.setStatus(terminal);
        return mapper.toResponse(order);
    }
}
