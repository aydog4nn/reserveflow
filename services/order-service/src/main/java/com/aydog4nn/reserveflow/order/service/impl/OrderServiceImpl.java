package com.aydog4nn.reserveflow.order.service.impl;

import com.aydog4nn.reserveflow.order.client.InventoryClient;
import com.aydog4nn.reserveflow.order.client.dto.InventoryProductResponse;
import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotencyId;
import com.aydog4nn.reserveflow.order.idempotency.OrderRequestFingerprint;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.service.persistence.OrderPersistenceService;
import com.aydog4nn.reserveflow.order.service.abs.IOrderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class OrderServiceImpl implements IOrderService {


    private static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("99999999999999999.99");

    private final InventoryClient inventoryClient;
    private final OrderPersistenceService orderPersistenceService;
    private final OrderRequestFingerprint requestFingerprint;

    public OrderServiceImpl(InventoryClient inventoryClient, OrderPersistenceService orderPersistenceService,
                            OrderRequestFingerprint requestFingerprint) {
        this.inventoryClient = inventoryClient;
        this.orderPersistenceService = orderPersistenceService;
        this.requestFingerprint = requestFingerprint;
    }


    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderResponse createOrder(CreateOrderRequest request, String clientId, String idempotencyKey) {
        validateCreateOrder(request);
        validateIdempotencyContext(clientId, idempotencyKey);
        RequestIdempotencyId id = new RequestIdempotencyId(clientId, idempotencyKey);
        String requestHash = requestFingerprint.hash(request);
        Optional<OrderResponse> existing = orderPersistenceService.findExisting(id, requestHash);
        if (existing.isPresent()) {
            return existing.get();
        }
        InventoryProductResponse product = findActiveProduct(request);
        BigDecimal totalAmount = calculateTotalAmount(product.unitPrice(), request.quantity());
        Order order = buildOrder(request, product, totalAmount);
        return saveOrReplay(order, id, requestHash);
    }


    private void validateCreateOrder(CreateOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Sipariş isteği zorunlu.");
        }
        if (request.productId() == null || request.paymentScenario() == null) {
            throw new IllegalArgumentException("Ürün kimliği ve ödeme senaryosu zorunlu.");
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new IllegalArgumentException("Sipariş miktarı pozitif olmalı.");
        }
    }

    private InventoryProductResponse findActiveProduct(CreateOrderRequest request) {
        InventoryProductResponse product = inventoryClient.getProduct(request.productId());
        if (!Boolean.TRUE.equals(product.active())) {
            throw new IllegalArgumentException("Ürün aktif değil!");
        }
        return product;
    }

    private BigDecimal calculateTotalAmount(BigDecimal unitPrice, int quantity) {
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (totalAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new IllegalArgumentException("Sipariş toplamı desteklenen tutar sınırını aşıyor.");
        }
        return totalAmount;
    }

    private Order buildOrder(
            CreateOrderRequest request, InventoryProductResponse product, BigDecimal totalAmount) {
        return new Order(product.id(), request.quantity(), product.unitPrice(),
                totalAmount, product.currency(), request.paymentScenario());
    }


    private OrderResponse saveOrReplay(Order order, RequestIdempotencyId id, String requestHash) {
        try {
            return orderPersistenceService.save(order, id, requestHash);
        } catch (DataIntegrityViolationException exception) {
            if (!isIdempotencyKeyConflict(exception)) {
                throw exception;
            }
            return orderPersistenceService.findExisting(id, requestHash)
                    .orElseThrow(() -> exception);
        }
    }

    private boolean isIdempotencyKeyConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "pk_request_idempotency".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private void validateIdempotencyContext(String clientId, String idempotencyKey) {
        validateClientId(clientId);
        validateIdempotencyKey(idempotencyKey);
    }

    private void validateClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Müşteri kimliği zorunlu.");
        }
        if (clientId.length() > 64) {
            throw new IllegalArgumentException("Müşteri kimliği en fazla 64 karakter olmalı.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Tekrarlı istek anahtarı zorunlu.");
        }
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Tekrarlı istek anahtarı en fazla 128 karakter olmalı.");
        }
    }





}
