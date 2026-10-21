package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.domain.ReservationStatus;
import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.repository.ReservationRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
import com.aydog4nn.reserveflow.inventory.service.impl.InventoryStockActionService;
import com.aydog4nn.reserveflow.inventory.messaging.consumer.StockActionConsumer;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockActionCommand;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockFinalizedEvent;
import com.aydog4nn.reserveflow.inventory.messaging.producer.StockFinalizedProducer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.amqp.AmqpException;
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
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers
class InventoryStockActionIntegrationTest {

    @Autowired
    private StockActionConsumer consumer;

    @MockitoBean
    private StockFinalizedProducer finalResults;

    @Test
    void consumerPublishesOnlyAfterStockCommitAndReplayKeepsStockCorrect() {
        Reservation reservation = reserve();
        UUID productId = productsFor(reservation);
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertStock(productId, 7, 0);
            assertEquals(ReservationStatus.COMMITTED,
                    reservations.findById(reservation.getId()).orElseThrow().getStatus());
            return null;
        }).when(finalResults).publish(any());
        StockActionCommand command = new StockActionCommand(reservation.getOrderId(), reservation.getId());
        consumer.commit(command);
        consumer.commit(command);
        verify(finalResults, times(2)).publish(new StockFinalizedEvent(
                reservation.getOrderId(), reservation.getId(), StockFinalizedEvent.Outcome.COMMITTED));
        assertStock(productId, 7, 0);
    }

    @Test
    void releaseConsumerPublishesMatchingResult() {
        Reservation reservation = reserve();
        consumer.release(new StockActionCommand(reservation.getOrderId(), reservation.getId()));
        verify(finalResults).publish(new StockFinalizedEvent(
                reservation.getOrderId(), reservation.getId(), StockFinalizedEvent.Outcome.RELEASED));
        assertStock(productsFor(reservation), 10, 0);
    }

    @Test
    void invalidActionDoesNotPublishSuccess() {
        Reservation reservation = reserve();
        assertThrows(IllegalArgumentException.class, () -> consumer.commit(
                new StockActionCommand(reservation.getOrderId(), UUID.randomUUID())));
        verifyNoInteractions(finalResults);
        assertStock(productsFor(reservation), 10, 3);
    }

    @Test
    void finalPublicationFailureLeavesCommittedStock() {
        Reservation reservation = reserve();
        doThrow(new AmqpException("Test result outage")).when(finalResults).publish(any());
        assertThrows(AmqpException.class, () -> consumer.commit(
                new StockActionCommand(reservation.getOrderId(), reservation.getId())));
        assertStock(productsFor(reservation), 7, 0);
        assertEquals(ReservationStatus.COMMITTED,
                reservations.findById(reservation.getId()).orElseThrow().getStatus());
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("stock_actions_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private InventoryStockActionService actions;
    @Autowired
    private IInventoryService inventory;
    @Autowired
    private IProductService products;
    @Autowired
    private StockRepository stocks;
    @Autowired
    private ReservationRepository reservations;

    @Test
    void commitDecreasesOnHandAndReservedOnlyOnce() {
        Reservation reservation = reserve();
        UUID productId = productsFor(reservation);
        actions.commit(reservation.getOrderId(), reservation.getId());
        assertStock(productId, 7, 0);
        assertEquals(ReservationStatus.COMMITTED, reservations.findById(reservation.getId()).orElseThrow().getStatus());
        actions.commit(reservation.getOrderId(), reservation.getId());
        assertStock(productId, 7, 0);
        assertThrows(IllegalArgumentException.class,
                () -> actions.release(reservation.getOrderId(), reservation.getId()));
        assertStock(productId, 7, 0);
    }

    @Test
    void releaseLeavesOnHandUnchangedAndCannotBeCommittedLater() {
        Reservation reservation = reserve();
        UUID productId = productsFor(reservation);
        actions.release(reservation.getOrderId(), reservation.getId());
        actions.release(reservation.getOrderId(), reservation.getId());
        assertStock(productId, 10, 0);
        assertEquals(ReservationStatus.RELEASED, reservations.findById(reservation.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> actions.commit(reservation.getOrderId(), reservation.getId()));
        assertStock(productId, 10, 0);
    }

    @Test
    void wrongReservationIdCannotChangeStock() {
        Reservation reservation = reserve();
        UUID productId = productsFor(reservation);
        assertThrows(IllegalArgumentException.class,
                () -> actions.commit(reservation.getOrderId(), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> actions.release(reservation.getOrderId(), UUID.randomUUID()));
        assertStock(productId, 10, 3);
        assertEquals(ReservationStatus.RESERVED, reservations.findById(reservation.getId()).orElseThrow().getStatus());
    }

    @Test
    void missingIdentifiersAndUnknownOrdersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> actions.commit(null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> actions.release(UUID.randomUUID(), null));
        assertThrows(IllegalArgumentException.class, () -> actions.commit(UUID.randomUUID(), UUID.randomUUID()));
    }

    private Reservation reserve() {
        var product = products.createProduct(new CreateProductRequest(
                "ACTION-" + UUID.randomUUID(), "Stock action product", BigDecimal.TEN, "TRY", 10));
        return inventory.reserveStock(UUID.randomUUID(), product.id(), 3);
    }

    private UUID productsFor(Reservation reservation) {
        return reservation.getProduct().getId();
    }

    private void assertStock(UUID productId, int onHand, int reserved) {
        var stock = stocks.findById(productId).orElseThrow();
        assertEquals(onHand, stock.getOnHand());
        assertEquals(reserved, stock.getReserved());
        assertEquals(onHand - reserved, stock.getAvailable());
    }
}
