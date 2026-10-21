package com.aydog4nn.reserveflow.order.service;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
import org.junit.jupiter.api.Test;
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
        "spring.rabbitmq.username=test",
        "spring.rabbitmq.password=test"
})
@Testcontainers
class OrderReservationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("order_reservation_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '5s'");
    }

    @Autowired
    private IOrderReservationService service;
    @Autowired
    private OrderRepository orders;

    @Test
    void recordsReservationAndTransitionsWithoutExplicitSave() {
        Order order = newOrder();
        UUID reservationId = UUID.randomUUID();
        service.handleStockReserved(order.getId(), reservationId);
        Order saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(reservationId, saved.getReservationId());
        assertEquals(OrderStatus.PAYMENT_PENDING, saved.getStatus());
        assertNull(saved.getPaymentId());
    }

    @Test
    void sameResultIsNoOpIncludingVersion() {
        Order order = newOrder();
        UUID reservationId = UUID.randomUUID();
        service.handleStockReserved(order.getId(), reservationId);
        Order before = orders.findById(order.getId()).orElseThrow();
        service.handleStockReserved(order.getId(), reservationId);
        Order after = orders.findById(order.getId()).orElseThrow();
        assertEquals(before.getVersion(), after.getVersion());
        assertEquals(before.getUpdatedAt(), after.getUpdatedAt());
        assertEquals(reservationId, after.getReservationId());
    }

    @Test
    void lateDuplicateDoesNotRegressAdvancedStatus() {
        Order order = newOrder();
        UUID reservationId = UUID.randomUUID();
        service.handleStockReserved(order.getId(), reservationId);
        Order advanced = orders.findById(order.getId()).orElseThrow();
        advanced.setStatus(OrderStatus.CONFIRMED);
        orders.saveAndFlush(advanced);
        service.handleStockReserved(order.getId(), reservationId);
        assertEquals(OrderStatus.CONFIRMED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void differentReservationCannotOverwriteOriginal() {
        Order order = newOrder();
        UUID reservationId = UUID.randomUUID();
        service.handleStockReserved(order.getId(), reservationId);
        assertThrows(IllegalArgumentException.class,
                () -> service.handleStockReserved(order.getId(), UUID.randomUUID()));
        assertEquals(reservationId, orders.findById(order.getId()).orElseThrow().getReservationId());
    }

    @Test
    void wrongStateRejectsResultWithoutMutation() {
        Order order = newOrder();
        order.setStatus(OrderStatus.CANCELLED);
        orders.saveAndFlush(order);
        assertThrows(IllegalArgumentException.class,
                () -> service.handleStockReserved(order.getId(), UUID.randomUUID()));
        Order saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, saved.getStatus());
        assertNull(saved.getReservationId());
    }

    @Test
    void rejectsMissingIdentifiersAndUnknownOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> service.handleStockReserved(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> service.handleStockReserved(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.handleStockReserved(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void concurrentSameResultIsAppliedOnce() throws Exception {
        Order order = newOrder();
        UUID reservationId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                service.handleStockReserved(order.getId(), reservationId);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                service.handleStockReserved(order.getId(), reservationId);
                return null;
            });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        Order saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAYMENT_PENDING, saved.getStatus());
        assertEquals(reservationId, saved.getReservationId());
        assertEquals(order.getVersion() + 1, saved.getVersion());
    }

    private Order newOrder() {
        return orders.saveAndFlush(new Order(UUID.randomUUID(), 3,
                new BigDecimal("1500.00"), new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS));
    }
}
