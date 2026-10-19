package com.aydog4nn.reserveflow.order.idempotency;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotency;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotencyId;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.repository.RequestIdempotencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class RequestIdempotencyIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("order_idempotency_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RequestIdempotencyRepository idempotencyRepository;
    @Autowired
    private OrderRepository orderRepository;

    @Test
    void persistsAndLooksUpByClientAndKey() {
        Order order = createOrder();
        String key = UUID.randomUUID().toString();
        idempotencyRepository.saveAndFlush(
                new RequestIdempotency("client-a", key, "a".repeat(64), order.getId()));
        RequestIdempotency persisted = idempotencyRepository.findById(
                new RequestIdempotencyId("client-a", key)).orElseThrow();
        assertEquals(order.getId(), persisted.getOrderId());
        assertEquals("a".repeat(64), persisted.getRequestHash());
        assertNotNull(persisted.getCreatedAt());
        assertFalse(persisted.isNew());
    }

    @Test
    void duplicateIdentityCannotOverwriteOriginalOrderOrHash() {
        Order first = createOrder();
        Order second = createOrder();
        String key = UUID.randomUUID().toString();
        var id = new RequestIdempotencyId("client-a", key);
        idempotencyRepository.saveAndFlush(
                new RequestIdempotency("client-a", key, "a".repeat(64), first.getId()));

        assertThrows(DataIntegrityViolationException.class, () ->
                idempotencyRepository.saveAndFlush(
                        new RequestIdempotency("client-a", key, "b".repeat(64), second.getId())));

        RequestIdempotency persisted = idempotencyRepository.findById(id).orElseThrow();
        assertEquals(first.getId(), persisted.getOrderId());
        assertEquals("a".repeat(64), persisted.getRequestHash());
    }

    @Test
    void differentClientsMayUseSameKeyIndependently() {
        String key = UUID.randomUUID().toString();
        Order first = createOrder();
        Order second = createOrder();
        idempotencyRepository.saveAndFlush(
                new RequestIdempotency("client-a", key, "a".repeat(64), first.getId()));
        idempotencyRepository.saveAndFlush(
                new RequestIdempotency("client-b", key, "b".repeat(64), second.getId()));
        assertEquals(first.getId(), idempotencyRepository.findById(
                new RequestIdempotencyId("client-a", key)).orElseThrow().getOrderId());
        assertEquals(second.getId(), idempotencyRepository.findById(
                new RequestIdempotencyId("client-b", key)).orElseThrow().getOrderId());
    }

    @Test
    void rejectsReferenceToMissingOrder() {
        long before = idempotencyRepository.count();
        assertThrows(DataIntegrityViolationException.class, () ->
                idempotencyRepository.saveAndFlush(new RequestIdempotency(
                        "client-a", UUID.randomUUID().toString(), "a".repeat(64), UUID.randomUUID())));
        assertEquals(before, idempotencyRepository.count());
    }

    private Order createOrder() {
        return orderRepository.saveAndFlush(new Order(
                UUID.randomUUID(), 2, new BigDecimal("10.00"), new BigDecimal("20.00"),
                "TRY", PaymentScenario.SUCCESS
        ));
    }
}
