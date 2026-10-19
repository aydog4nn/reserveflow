package com.aydog4nn.reserveflow.order.service.persistence;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotency;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotencyId;
import com.aydog4nn.reserveflow.order.idempotency.IdempotencyConflictException;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.repository.RequestIdempotencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final RequestIdempotencyRepository idempotencyRepository;

    public OrderPersistenceService(OrderRepository orderRepository, OrderMapper orderMapper,
                                   RequestIdempotencyRepository idempotencyRepository) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public OrderResponse save(Order order, RequestIdempotencyId id, String requestHash) {
        Order savedOrder = orderRepository.saveAndFlush(order);
        idempotencyRepository.saveAndFlush(new RequestIdempotency(
                id.getClientId(), id.getIdempotencyKey(), requestHash, savedOrder.getId()
        ));
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> findExisting(RequestIdempotencyId id, String requestHash) {
        return idempotencyRepository.findById(id).map(record -> {
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException();
            }
            Order order = orderRepository.findById(record.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("Tekrar kaydının siparişi bulunamadı."));
            return orderMapper.toResponse(order);
        });
    }
}
