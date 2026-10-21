package com.aydog4nn.reserveflow.order.service;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.messaging.dto.StockFinalizedEvent;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.service.impl.OrderCompletionService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.username=test", "spring.rabbitmq.password=test"
})
@Testcontainers
class OrderCompletionServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("completion_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '5s'");
    }

    @Autowired
    private OrderCompletionService service;
    @Autowired
    private OrderRepository orders;

    @ParameterizedTest
    @EnumSource(StockFinalizedEvent.Outcome.class)
    void closesOnlyAfterMatchingStockResultAndDuplicateIsNoOp(StockFinalizedEvent.Outcome outcome) {
        Order order = waitingOrder(outcome);
        StockFinalizedEvent event = new StockFinalizedEvent(order.getId(), order.getReservationId(), outcome);
        var result = service.handleStockFinalized(event);
        assertEquals(outcome == StockFinalizedEvent.Outcome.COMMITTED
                ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED, result.status());
        assertEquals(order.getPaymentId(), result.paymentId());
        Long version = orders.findById(order.getId()).orElseThrow().getVersion();
        service.handleStockFinalized(event);
        assertEquals(version, orders.findById(order.getId()).orElseThrow().getVersion());
        StockFinalizedEvent.Outcome opposite = outcome == StockFinalizedEvent.Outcome.COMMITTED
                ? StockFinalizedEvent.Outcome.RELEASED : StockFinalizedEvent.Outcome.COMMITTED;
        assertThrows(IllegalArgumentException.class, () -> service.handleStockFinalized(
                new StockFinalizedEvent(order.getId(), order.getReservationId(), opposite)));
    }

    @Test
    void wrongReservationAndPrematureResultCannotCloseOrder() {
        Order order = waitingOrder(StockFinalizedEvent.Outcome.COMMITTED);
        assertThrows(IllegalArgumentException.class, () -> service.handleStockFinalized(
                new StockFinalizedEvent(order.getId(), UUID.randomUUID(), StockFinalizedEvent.Outcome.COMMITTED)));
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orders.saveAndFlush(order);
        assertThrows(IllegalArgumentException.class, () -> service.handleStockFinalized(
                new StockFinalizedEvent(order.getId(), order.getReservationId(), StockFinalizedEvent.Outcome.COMMITTED)));
        assertEquals(OrderStatus.PAYMENT_PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void missingPaymentCannotCloseOrder() {
        Order order = waitingOrder(StockFinalizedEvent.Outcome.RELEASED);
        order.setPaymentId(null);
        orders.saveAndFlush(order);
        assertThrows(IllegalArgumentException.class, () -> service.handleStockFinalized(
                new StockFinalizedEvent(order.getId(), order.getReservationId(), StockFinalizedEvent.Outcome.RELEASED)));
    }

    @Test
    void rejectsUnknownOrInvalidResult() {
        assertThrows(IllegalArgumentException.class, () -> service.handleStockFinalized(null));
        assertThrows(ConstraintViolationException.class,
                () -> service.handleStockFinalized(new StockFinalizedEvent(null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.handleStockFinalized(
                new StockFinalizedEvent(UUID.randomUUID(), UUID.randomUUID(), StockFinalizedEvent.Outcome.COMMITTED)));
    }

    @Test
    void concurrentFinalResultUpdatesOrderOnce() throws Exception {
        Order order = waitingOrder(StockFinalizedEvent.Outcome.COMMITTED);
        var event = new StockFinalizedEvent(order.getId(), order.getReservationId(), StockFinalizedEvent.Outcome.COMMITTED);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.handleStockFinalized(event); });
            var second = executor.submit(() -> { start.await(); return service.handleStockFinalized(event); });
            start.countDown();
            assertEquals(OrderStatus.CONFIRMED, first.get(15, TimeUnit.SECONDS).status());
            assertEquals(OrderStatus.CONFIRMED, second.get(15, TimeUnit.SECONDS).status());
        }
        assertEquals(order.getVersion() + 1, orders.findById(order.getId()).orElseThrow().getVersion());
    }

    private Order waitingOrder(StockFinalizedEvent.Outcome outcome) {
        Order order = new Order(UUID.randomUUID(), 3, new BigDecimal("1500.00"),
                new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS);
        order.setReservationId(UUID.randomUUID());
        order.setPaymentId(UUID.randomUUID());
        order.setStatus(outcome == StockFinalizedEvent.Outcome.COMMITTED
                ? OrderStatus.STOCK_COMMIT_PENDING : OrderStatus.COMPENSATION_PENDING);
        if (outcome == StockFinalizedEvent.Outcome.RELEASED) {
            order.setFailureReason("Ödeme reddedildi.");
        }
        return orders.saveAndFlush(order);
    }
}
