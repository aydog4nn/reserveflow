package com.aydog4nn.reserveflow.order.service.impl;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.messaging.dto.PaymentResultEvent;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderPaymentService {

    private final OrderRepository orders;
    private final OrderMapper mapper;
    private final Validator validator;

    public OrderPaymentService(OrderRepository orders, OrderMapper mapper, Validator validator) {
        this.orders = orders;
        this.mapper = mapper;
        this.validator = validator;
    }

    @Transactional
    public OrderResponse handlePaymentResult(PaymentResultEvent event) {
        validateResult(event);
        Order order = orders.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı."));
        if (order.getTotalAmount().compareTo(event.amount()) != 0
                || !order.getCurrency().equals(event.currency())) {
            throw new IllegalArgumentException("Ödeme tutarı veya para birimi siparişle uyuşmuyor.");
        }
        boolean succeeded = event.outcome() == PaymentResultEvent.Outcome.SUCCEEDED;
        OrderStatus next = succeeded ? OrderStatus.STOCK_COMMIT_PENDING : OrderStatus.COMPENSATION_PENDING;
        OrderStatus terminal = succeeded ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
        if (order.getPaymentId() != null) {
            if (!order.getPaymentId().equals(event.paymentId())
                    || (order.getStatus() != next && order.getStatus() != terminal)
                    || !java.util.Objects.equals(order.getFailureReason(), event.failureReason())) {
                throw new IllegalArgumentException("Ödeme sonucu önceki kayıtla çelişiyor.");
            }
            return mapper.toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING || order.getReservationId() == null) {
            throw new IllegalArgumentException("Sipariş ödeme sonucu beklemiyor.");
        }
        order.setPaymentId(event.paymentId());
        order.setFailureReason(event.failureReason());
        order.setStatus(next);
        return mapper.toResponse(order);
    }

    private void validateResult(PaymentResultEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Ödeme sonucu zorunlu.");
        }
        var violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        if (event.outcome() == PaymentResultEvent.Outcome.FAILED
                && (event.failureReason() == null || event.failureReason().isBlank())) {
            throw new IllegalArgumentException("Reddedilen ödemenin nedeni zorunlu.");
        }
        if (event.outcome() == PaymentResultEvent.Outcome.SUCCEEDED && event.failureReason() != null) {
            throw new IllegalArgumentException("Başarılı ödeme hata nedeni taşıyamaz.");
        }
    }
}
