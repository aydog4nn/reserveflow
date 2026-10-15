package com.aydog4nn.reserveflow.inventory.service;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.domain.ReservationStatus;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ReservationLifecycleTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("inventory_lifecycle_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET lock_timeout = '5s'");
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

    @Test
    void commitDecreasesBothStockValuesAndRepeatDoesNotChangeThemAgain() {
        Reservation reservation = createReservation();
        Reservation committed = inventoryService.commitReservation(reservation.getOrderId());
        assertEquals(reservation.getId(), committed.getId());
        assertEquals(ReservationStatus.COMMITTED, committed.getStatus());
        assertStockAndState(reservation, 7, 0, 7, ReservationStatus.COMMITTED);

        Reservation repeated = inventoryService.commitReservation(reservation.getOrderId());
        assertEquals(committed.getId(), repeated.getId());
        assertStockAndState(reservation, 7, 0, 7, ReservationStatus.COMMITTED);
    }

    @Test
    void releaseOnlyDecreasesReservedAndRepeatDoesNotChangeItAgain() {
        Reservation reservation = createReservation();
        Reservation released = inventoryService.releaseReservation(reservation.getOrderId());
        assertEquals(reservation.getId(), released.getId());
        assertEquals(ReservationStatus.RELEASED, released.getStatus());
        assertStockAndState(reservation, 10, 0, 10, ReservationStatus.RELEASED);

        Reservation repeated = inventoryService.releaseReservation(reservation.getOrderId());
        assertEquals(released.getId(), repeated.getId());
        assertStockAndState(reservation, 10, 0, 10, ReservationStatus.RELEASED);
    }

    @Test
    void committedReservationCannotBeReleased() {
        Reservation reservation = createReservation();
        inventoryService.commitReservation(reservation.getOrderId());
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.releaseReservation(reservation.getOrderId()));
        assertStockAndState(reservation, 7, 0, 7, ReservationStatus.COMMITTED);
    }

    @Test
    void releasedReservationCannotBeCommitted() {
        Reservation reservation = createReservation();
        inventoryService.releaseReservation(reservation.getOrderId());
        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.commitReservation(reservation.getOrderId()));
        assertStockAndState(reservation, 10, 0, 10, ReservationStatus.RELEASED);
    }

    @Test
    void missingAndNullOrderIdsAreRejected() {
        UUID missingOrderId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> inventoryService.commitReservation(missingOrderId));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.releaseReservation(missingOrderId));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.commitReservation(null));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.releaseReservation(null));
    }

    @RepeatedTest(5)
    void concurrentCommitAndReleaseAllowOnlyOneTransition() throws Exception {
        Reservation reservation = createReservation();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> outcomes;

        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var commit = executor.submit(() -> transitionAfterStart(
                        reservation.getOrderId(), true, ready, start));
                var release = executor.submit(() -> transitionAfterStart(
                        reservation.getOrderId(), false, ready, start));
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                outcomes = List.of(commit.get(15, TimeUnit.SECONDS), release.get(15, TimeUnit.SECONDS));
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }

        assertEquals(1L, outcomes.stream().filter(Boolean::booleanValue).count());
        Reservation persisted = reservationRepository.findByOrderId(reservation.getOrderId()).orElseThrow();
        ReservationStatus expected = outcomes.getFirst()
                ? ReservationStatus.COMMITTED : ReservationStatus.RELEASED;
        assertEquals(expected, persisted.getStatus());
        assertStockAndState(reservation, expected == ReservationStatus.COMMITTED ? 7 : 10,
                0, expected == ReservationStatus.COMMITTED ? 7 : 10, expected);
    }

    private boolean transitionAfterStart(
            UUID orderId, boolean commit, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            if (commit) {
                inventoryService.commitReservation(orderId);
            } else {
                inventoryService.releaseReservation(orderId);
            }
            return true;
        } catch (IllegalArgumentException exception) {
            assertEquals(commit ? "Serbest bırakılmış rezervasyon kesinleştirilemez."
                    : "Kesinleşmiş rezervasyon serbest bırakılamaz.", exception.getMessage());
            return false;
        }
    }

    private Reservation createReservation() {
        ProductResponse product = productService.createProduct(new CreateProductRequest(
                "LIFECYCLE-" + UUID.randomUUID(), "Lifecycle product",
                new BigDecimal("100.00"), "TRY", 10
        ));
        return inventoryService.reserveStock(UUID.randomUUID(), product.id(), 3);
    }

    private void assertStockAndState(
            Reservation reservation, int onHand, int reserved, int available, ReservationStatus status) {
        Stock stock = stockRepository.findById(reservation.getProduct().getId()).orElseThrow();
        assertEquals(onHand, stock.getOnHand());
        assertEquals(reserved, stock.getReserved());
        assertEquals(available, stock.getAvailable());
        assertEquals(status, reservationRepository.findByOrderId(reservation.getOrderId()).orElseThrow().getStatus());
    }
}
