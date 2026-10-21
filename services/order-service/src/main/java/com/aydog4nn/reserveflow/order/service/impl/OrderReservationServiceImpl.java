package com.aydog4nn.reserveflow.order.service.impl;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderReservationServiceImpl implements IOrderReservationService {

    private final OrderRepository orderRepository;

    public OrderReservationServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    @Override
    public void handleStockReserved(UUID orderId, UUID reservationId) {
        validateIdentifiers(orderId, reservationId);
        Order order = findOrderForUpdate(orderId);
        if (reservationId.equals(order.getReservationId())) {
            return;
        }
        validateReservationTransition(order);
        order.setReservationId(reservationId);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
    }

    private void validateIdentifiers(UUID orderId, UUID reservationId) {
        if (orderId == null || reservationId == null) {
            throw new IllegalArgumentException("Sipariş ve rezervasyon kimliği zorunlu.");
        }
    }

    private Order findOrderForUpdate(UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı."));
    }

    private void validateReservationTransition(Order order) {
        if (order.getReservationId() != null) {
            throw new IllegalArgumentException("Sipariş için farklı bir rezervasyon zaten kayıtlı.");
        }
        if (order.getStatus() != OrderStatus.PENDING_STOCK) {
            throw new IllegalArgumentException("Yalnız stok bekleyen siparişe rezervasyon sonucu işlenebilir.");
        }
    }
}
