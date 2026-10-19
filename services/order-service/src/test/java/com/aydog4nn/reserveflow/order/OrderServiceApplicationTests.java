package com.aydog4nn.reserveflow.order;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import jakarta.validation.Validator;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderServiceApplicationTests {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private Validator validator;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("order_test")
            .withUsername("order_test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void persistsOrderAndMapsPriceSnapshotToResponse() {
        UUID productId = UUID.randomUUID();
        Order order = orderRepository.saveAndFlush(new Order(
                productId, 2, new BigDecimal("1500.00"), new BigDecimal("3000.00"),
                "TRY", PaymentScenario.SUCCESS
        ));
        Order persisted = orderRepository.findById(order.getId()).orElseThrow();
        OrderResponse response = orderMapper.toResponse(persisted);

        assertNotNull(response.orderId());
        assertEquals(productId, response.productId());
        assertEquals(2, response.quantity());
        assertEquals(new BigDecimal("1500.00"), response.unitPrice());
        assertEquals(new BigDecimal("3000.00"), response.totalAmount());
        assertEquals("TRY", response.currency());
        assertEquals(PaymentScenario.SUCCESS, response.paymentScenario());
        assertEquals(OrderStatus.PENDING_STOCK, response.status());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
        assertEquals(0L, persisted.getVersion());
    }

    @Test
    void rejectsInconsistentTotalAtDatabaseLevel() {
        long before = orderRepository.count();
        assertThrows(DataIntegrityViolationException.class, () ->
                orderRepository.saveAndFlush(new Order(
                        UUID.randomUUID(), 2, new BigDecimal("1500.00"), new BigDecimal("1.00"),
                        "TRY", PaymentScenario.SUCCESS
                )));
        assertEquals(before, orderRepository.count());
    }

    @Test
    void validatesCreateOrderRequestFields() {
        assertTrue(validator.validate(
                new CreateOrderRequest(UUID.randomUUID(), 2, PaymentScenario.SUCCESS)).isEmpty());
        assertEquals(3, validator.validate(new CreateOrderRequest(null, null, null)).size());
        assertFalse(validator.validate(
                new CreateOrderRequest(UUID.randomUUID(), 0, PaymentScenario.SUCCESS)).isEmpty());
        assertFalse(validator.validate(
                new CreateOrderRequest(UUID.randomUUID(), -1, PaymentScenario.SUCCESS)).isEmpty());
    }

    @Test
    void updatesVersionAndTimestampForManagedChanges() {
        Order order = orderRepository.saveAndFlush(new Order(
                UUID.randomUUID(), 1, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "TRY", PaymentScenario.DELAY
        ));
        order.setReservationId(UUID.randomUUID());
        Order updated = orderRepository.saveAndFlush(order);
        assertEquals(1L, updated.getVersion());
        assertEquals(order.getReservationId(), updated.getReservationId());
        assertFalse(updated.getUpdatedAt().isBefore(updated.getCreatedAt()));
    }
}
