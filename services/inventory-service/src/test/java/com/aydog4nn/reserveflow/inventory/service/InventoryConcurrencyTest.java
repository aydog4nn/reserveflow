package com.aydog4nn.reserveflow.inventory.service;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.repository.ReservationRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class InventoryConcurrencyTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("inventory_concurrency_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET lock_timeout = '5s'");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private IProductService productService;

    @Autowired
    private IInventoryService inventoryService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rollsBackProductWhenStockInsertFails() {
        String sku = "ROLLBACK-" + UUID.randomUUID();
        long productsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM products", Long.class);
        long stockBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM stock", Long.class);
        jdbcTemplate.execute("""
                ALTER TABLE stock ADD CONSTRAINT test_reject_stock_13
                CHECK (on_hand <> 13)
                """);

        try {
            CreateProductRequest request = new CreateProductRequest(
                    sku, "Rollback test product", new BigDecimal("100.00"), "TRY", 13
            );

            DataIntegrityViolationException failure = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> productService.createProduct(request)
            );

            assertTrue(failure.getMostSpecificCause().getMessage().contains("test_reject_stock_13"));
            assertEquals(0L, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM products WHERE sku = ?", Long.class, sku));
            assertEquals(productsBefore,
                    jdbcTemplate.queryForObject("SELECT count(*) FROM products", Long.class));
            assertEquals(stockBefore,
                    jdbcTemplate.queryForObject("SELECT count(*) FROM stock", Long.class));
        } finally {
            jdbcTemplate.execute("ALTER TABLE stock DROP CONSTRAINT test_reject_stock_13");
        }

        ProductResponse recovered = productService.createProduct(new CreateProductRequest(
                sku, "Rollback test product", new BigDecimal("100.00"), "TRY", 13
        ));
        assertEquals(13, recovered.onHand());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM products WHERE sku = ?", Long.class, sku));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM stock WHERE product_id = ?", Long.class, recovered.id()));
    }

    @RepeatedTest(5)
    void onlyOneOrderCanReserveFourItemsFromFiveAvailable() throws Exception {
        ProductResponse product = productService.createProduct(new CreateProductRequest(
                "CONCURRENT-" + UUID.randomUUID(), "Concurrent test product",
                new BigDecimal("100.00"), "TRY", 5
        ));
        UUID firstOrderId = UUID.randomUUID();
        UUID secondOrderId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> outcomes;

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> reserveAfterStart(
                        firstOrderId, product.id(), ready, start));
                var second = executor.submit(() -> reserveAfterStart(
                        secondOrderId, product.id(), ready, start));
                assertTrue(ready.await(10, TimeUnit.SECONDS), "İki iş parçacığı da hazır olmalı.");
                start.countDown();
                outcomes = List.of(
                        first.get(15, TimeUnit.SECONDS),
                        second.get(15, TimeUnit.SECONDS)
                );
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, outcomes.stream().filter(outcome -> !outcome).count());

        Stock stock = stockRepository.findById(product.id()).orElseThrow();
        assertEquals(5, stock.getOnHand());
        assertEquals(4, stock.getReserved());
        assertEquals(1, stock.getAvailable());

        List<Reservation> reservations = java.util.stream.Stream.of(firstOrderId, secondOrderId)
                .map(reservationRepository::findByOrderId)
                .flatMap(java.util.Optional::stream)
                .toList();
        assertEquals(1, reservations.size());
        assertEquals(4, reservations.getFirst().getQuantity());
        assertEquals(product.id(), reservations.getFirst().getProduct().getId());
    }

    private boolean reserveAfterStart(
            UUID orderId, UUID productId, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS), "Başlangıç sinyali gelmeli.");
        try {
            Reservation reservation = inventoryService.reserveStock(orderId, productId, 4);
            assertNotNull(reservation.getId());
            return true;
        } catch (IllegalArgumentException exception) {
            assertEquals("Yetersiz stok.", exception.getMessage());
            return false;
        }
    }
}
