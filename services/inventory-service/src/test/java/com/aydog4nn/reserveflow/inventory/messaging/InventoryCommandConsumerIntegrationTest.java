package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.domain.ReservationStatus;
import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.messaging.consumer.InventoryCommandConsumer;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.inventory.repository.ReservationRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers
class InventoryCommandConsumerIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("consumer_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private InventoryCommandConsumer consumer;
    @Autowired
    private IProductService products;
    @Autowired
    private StockRepository stocks;
    @Autowired
    private ReservationRepository reservations;

    @Test
    void commandCommitsReservationAndSequentialReplayDoesNotReserveTwice() {
        UUID productId = createProduct();
        UUID orderId = UUID.randomUUID();
        ReserveStockCommand command = new ReserveStockCommand(orderId, productId, 3);
        consumer.receive(command);
        var first = reservations.findByOrderId(orderId).orElseThrow();
        assertEquals(ReservationStatus.RESERVED, first.getStatus());
        assertEquals(3, first.getQuantity());
        assertEquals(3, stocks.findById(productId).orElseThrow().getReserved());
        assertEquals(10, stocks.findById(productId).orElseThrow().getOnHand());

        consumer.receive(command);
        assertEquals(first.getId(), reservations.findByOrderId(orderId).orElseThrow().getId());
        assertEquals(3, stocks.findById(productId).orElseThrow().getReserved());
        assertThrows(IllegalArgumentException.class,
                () -> consumer.receive(new ReserveStockCommand(orderId, productId, 4)));
        assertEquals(3, stocks.findById(productId).orElseThrow().getReserved());
    }

    @Test
    void insufficientStockRollsBackWithoutReservation() {
        UUID productId = createProduct();
        UUID orderId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> consumer.receive(new ReserveStockCommand(orderId, productId, 11)));
        assertTrue(reservations.findByOrderId(orderId).isEmpty());
        assertEquals(0, stocks.findById(productId).orElseThrow().getReserved());
        assertEquals(10, stocks.findById(productId).orElseThrow().getOnHand());
    }

    private UUID createProduct() {
        return products.createProduct(new CreateProductRequest(
                "CONSUMER-" + UUID.randomUUID(), "Consumer test product",
                new BigDecimal("1500.00"), "TRY", 10)).id();
    }
}
