package com.aydog4nn.reserveflow.order.service;

import com.aydog4nn.reserveflow.order.client.InventoryClient;
import com.aydog4nn.reserveflow.order.client.dto.InventoryProductResponse;
import com.aydog4nn.reserveflow.order.client.exception.InventoryUnavailableException;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.mapper.OrderMapper;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.service.abs.IOrderService;
import com.aydog4nn.reserveflow.order.service.abs.IOrderReservationService;
import com.aydog4nn.reserveflow.order.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.InventoryCommandProducer;
import org.springframework.amqp.AmqpException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotencyId;
import com.aydog4nn.reserveflow.order.idempotency.IdempotencyConflictException;
import com.aydog4nn.reserveflow.order.idempotency.OrderRequestFingerprint;
import com.aydog4nn.reserveflow.order.repository.RequestIdempotencyRepository;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.username=test",
        "spring.rabbitmq.password=test"
})
@Testcontainers
@Import(OrderCreationIntegrationTest.TestBeans.class)
class OrderCreationIntegrationTest {

    @MockitoBean
    private InventoryCommandProducer commandProducer;

    @Autowired
    private IOrderReservationService reservationService;

    @Test
    void publishesOnlyAfterCommitAndRepublishesPendingReplay() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest request = request(3);
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            ReserveStockCommand command = invocation.getArgument(0);
            assertTrue(orderRepository.findById(command.orderId()).isPresent());
            assertTrue(idempotencyRepository.findById(new RequestIdempotencyId("publish-client", key)).isPresent());
            return null;
        }).when(commandProducer).sendReserveStock(any());
        OrderResponse first = orderService.createOrder(request, "publish-client", key);
        OrderResponse replay = orderService.createOrder(request, "publish-client", key);
        assertEquals(first.orderId(), replay.orderId());
        verify(commandProducer, times(2)).sendReserveStock(
                new ReserveStockCommand(first.orderId(), request.productId(), 3));
    }

    @Test
    void completedReservationReplayDoesNotPublishAgain() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest request = request(3);
        OrderResponse first = orderService.createOrder(request, "completed-client", key);
        reservationService.handleStockReserved(first.orderId(), UUID.randomUUID());
        OrderResponse replay = orderService.createOrder(request, "completed-client", key);
        assertEquals(OrderStatus.PAYMENT_PENDING, replay.status());
        verify(commandProducer, times(1)).sendReserveStock(any());
    }

    @Test
    void brokerFailureKeepsOrderAndSameKeyCanRetryPublication() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest request = request(3);
        AmqpException failure = new AmqpException("Test broker outage");
        doThrow(failure).when(commandProducer).sendReserveStock(any());
        assertSame(failure, assertThrows(AmqpException.class,
                () -> orderService.createOrder(request, "failed-publish-client", key)));
        var record = idempotencyRepository.findById(
                new RequestIdempotencyId("failed-publish-client", key)).orElseThrow();
        assertEquals(OrderStatus.PENDING_STOCK,
                orderRepository.findById(record.getOrderId()).orElseThrow().getStatus());
        doNothing().when(commandProducer).sendReserveStock(any());
        OrderResponse replay = orderService.createOrder(request, "failed-publish-client", key);
        assertEquals(record.getOrderId(), replay.orderId());
        verify(commandProducer, times(2)).sendReserveStock(
                new ReserveStockCommand(replay.orderId(), request.productId(), 3));
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("order_creation_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IOrderService orderService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private RecordingInventoryClient inventoryClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RequestIdempotencyRepository idempotencyRepository;

    @BeforeEach
    void resetClient() {
        inventoryClient.price = new BigDecimal("1500.00");
        inventoryClient.active = true;
        inventoryClient.failure = null;
        inventoryClient.calls = 0;
        inventoryClient.transactionObserved = false;
        inventoryClient.simultaneousReads = null;
    }

    @Test
    void persistsPriceSnapshotAndMapsInsideTransaction() {
        CreateOrderRequest request = request(2);
        OrderResponse response = createOrder(request);
        var persisted = orderRepository.findById(response.orderId()).orElseThrow();

        assertEquals(request.productId(), response.productId());
        assertEquals(2, response.quantity());
        assertEquals(new BigDecimal("1500.00"), response.unitPrice());
        assertEquals(new BigDecimal("3000.00"), response.totalAmount());
        assertEquals("TRY", response.currency());
        assertEquals(OrderStatus.PENDING_STOCK, response.status());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
        assertEquals(response.totalAmount(), persisted.getTotalAmount());
        assertEquals(1, inventoryClient.calls);
        assertFalse(inventoryClient.transactionObserved);
    }

    @Test
    void rejectsInvalidRequestsBeforeCallingInventoryOrSaving() {
        long before = orderRepository.count();
        CreateOrderRequest[] invalid = {
                null,
                new CreateOrderRequest(null, 1, PaymentScenario.SUCCESS),
                new CreateOrderRequest(UUID.randomUUID(), 1, null),
                new CreateOrderRequest(UUID.randomUUID(), null, PaymentScenario.SUCCESS),
                request(0),
                request(-1)
        };
        for (CreateOrderRequest request : invalid) {
            assertThrows(IllegalArgumentException.class, () -> createOrder(request));
        }
        assertEquals(0, inventoryClient.calls);
        assertEquals(before, orderRepository.count());
    }

    @Test
    void rejectsInactiveProductWithoutSaving() {
        long before = orderRepository.count();
        inventoryClient.active = false;
        assertThrows(IllegalArgumentException.class, () -> createOrder(request(1)));
        assertEquals(before, orderRepository.count());
    }

    @Test
    void rejectsInvalidIdempotencyMetadataBeforeClientOrSaving() {
        long before = orderRepository.count();
        String[][] invalid = {
                {null, "key"},
                {"", "key"},
                {" \t", "key"},
                {"a".repeat(65), "key"},
                {"client", null},
                {"client", ""},
                {"client", " \t"},
                {"client", "a".repeat(129)}
        };
        for (String[] metadata : invalid) {
            assertThrows(IllegalArgumentException.class, () ->
                    orderService.createOrder(request(1), metadata[0], metadata[1]));
        }
        assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(null, "client", "key"));
        assertEquals(0, inventoryClient.calls);
        assertEquals(before, orderRepository.count());
    }

    @Test
    void acceptsMetadataBoundaries() {
        long before = orderRepository.count();
        assertNotNull(orderService.createOrder(request(1), "a".repeat(64), "b".repeat(128)).orderId());
        assertNotNull(orderService.createOrder(request(1), "client", "key").orderId());
        assertEquals(2, inventoryClient.calls);
        assertEquals(before + 2, orderRepository.count());
    }

    @Test
    void propagatesInventoryFailureWithoutSaving() {
        long before = orderRepository.count();
        inventoryClient.failure = new InventoryUnavailableException("Inventory unavailable");
        var failure = assertThrows(InventoryUnavailableException.class,
                () -> createOrder(request(1)));
        assertSame(inventoryClient.failure, failure);
        assertFalse(inventoryClient.transactionObserved);
        assertEquals(before, orderRepository.count());
    }

    @Test
    void rejectsTotalOverflowBeforeSaving() {
        long before = orderRepository.count();
        inventoryClient.price = new BigDecimal("99999999999999999.99");
        assertThrows(IllegalArgumentException.class, () -> createOrder(request(2)));
        assertEquals(before, orderRepository.count());
    }

    @Test
    void acceptsMaximumRepresentableTotalAndZeroPrice() {
        inventoryClient.price = new BigDecimal("99999999999999999.99");
        assertEquals(inventoryClient.price, createOrder(request(1)).totalAmount());
        inventoryClient.price = new BigDecimal("0.00");
        assertEquals(new BigDecimal("0.00"), createOrder(request(2)).totalAmount());
    }

    @Test
    void databaseFailureRollsBackAndServiceRecoversAfterConstraintRemoved() {
        long before = orderRepository.count();
        jdbcTemplate.execute("""
                ALTER TABLE orders ADD CONSTRAINT test_reject_quantity_13 CHECK (quantity <> 13)
                """);
        try {
            var failure = assertThrows(DataIntegrityViolationException.class,
                    () -> createOrder(request(13)));
            assertTrue(failure.getMostSpecificCause().getMessage().contains("test_reject_quantity_13"));
            assertEquals(before, orderRepository.count());
        } finally {
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT test_reject_quantity_13");
        }
        assertNotNull(createOrder(request(13)).orderId());
        assertEquals(before + 1, orderRepository.count());
    }


    @Test
    void replayReturnsOriginalSnapshotWithoutCallingInventory() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest request = request(2);
        long before = orderRepository.count();
        OrderResponse first = orderService.createOrder(request, "client-a", key);
        inventoryClient.price = new BigDecimal("2000.00");
        inventoryClient.failure = new InventoryUnavailableException("Must not call Inventory on replay");

        OrderResponse replay = orderService.createOrder(request, "client-a", key);

        assertEquals(first.orderId(), replay.orderId());
        assertEquals(first.unitPrice(), replay.unitPrice());
        assertEquals(first.totalAmount(), replay.totalAmount());
        assertEquals(before + 1, orderRepository.count());
        assertEquals(1, inventoryClient.calls);
    }

    @Test
    void sameKeyWithDifferentPayloadConflictsBeforeInventoryCall() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest original = request(2);
        OrderResponse first = orderService.createOrder(original, "client-a", key);
        long before = orderRepository.count();
        var changed = new CreateOrderRequest(original.productId(), 3, original.paymentScenario());
        assertThrows(IdempotencyConflictException.class,
                () -> orderService.createOrder(changed, "client-a", key));
        assertEquals(before, orderRepository.count());
        assertTrue(orderRepository.existsById(first.orderId()));
        assertEquals(1, inventoryClient.calls);
    }

    @Test
    void sameKeyForDifferentClientsCreatesIndependentOrders() {
        String key = UUID.randomUUID().toString();
        CreateOrderRequest request = request(2);
        OrderResponse first = orderService.createOrder(request, "client-a", key);
        OrderResponse second = orderService.createOrder(request, "client-b", key);
        assertFalse(first.orderId().equals(second.orderId()));
        assertEquals(2, inventoryClient.calls);
    }

    @Test
    void newKeyForSameClientCreatesNewOrder() {
        CreateOrderRequest request = request(2);
        OrderResponse first = orderService.createOrder(request, "client-a", UUID.randomUUID().toString());
        OrderResponse second = orderService.createOrder(request, "client-a", UUID.randomUUID().toString());
        assertFalse(first.orderId().equals(second.orderId()));
        assertEquals(2, inventoryClient.calls);
    }

    @Test
    void failedIdempotencyInsertRollsBackOrderAndDoesNotBecomeReplay() {
        long ordersBefore = orderRepository.count();
        long keysBefore = idempotencyRepository.count();
        jdbcTemplate.execute("""
                ALTER TABLE request_idempotency ADD CONSTRAINT test_reject_idempotency_insert
                CHECK (client_id <> 'blocked-client')
                """);
        try {
            var failure = assertThrows(DataIntegrityViolationException.class, () ->
                    orderService.createOrder(request(1), "blocked-client", UUID.randomUUID().toString()));
            assertTrue(failure.getMostSpecificCause().getMessage().contains("test_reject_idempotency_insert"));
            assertEquals(ordersBefore, orderRepository.count());
            assertEquals(keysBefore, idempotencyRepository.count());
        } finally {
            jdbcTemplate.execute("ALTER TABLE request_idempotency DROP CONSTRAINT test_reject_idempotency_insert");
        }
    }

    @RepeatedTest(3)
    void concurrentSamePayloadCreatesOneOrderAndReturnsSameId() throws Exception {
        CreateOrderRequest request = request(2);
        String key = UUID.randomUUID().toString();
        long ordersBefore = orderRepository.count();
        long keysBefore = idempotencyRepository.count();

        List<Object> results = race(request, request, key);

        assertTrue(results.get(0) instanceof OrderResponse);
        assertTrue(results.get(1) instanceof OrderResponse);
        assertEquals(((OrderResponse) results.get(0)).orderId(), ((OrderResponse) results.get(1)).orderId());
        assertEquals(ordersBefore + 1, orderRepository.count());
        assertEquals(keysBefore + 1, idempotencyRepository.count());
        assertEquals(2, inventoryClient.calls);
        assertFalse(inventoryClient.transactionObserved);
    }

    @RepeatedTest(3)
    void concurrentDifferentPayloadCreatesOneOrderAndOneConflict() throws Exception {
        CreateOrderRequest first = request(2);
        var second = new CreateOrderRequest(first.productId(), 3, first.paymentScenario());
        String key = UUID.randomUUID().toString();
        long ordersBefore = orderRepository.count();
        long keysBefore = idempotencyRepository.count();

        List<Object> results = race(first, second, key);

        assertEquals(1L, results.stream().filter(OrderResponse.class::isInstance).count());
        assertEquals(1L, results.stream().filter(IdempotencyConflictException.class::isInstance).count());
        OrderResponse winner = (OrderResponse) results.stream().filter(OrderResponse.class::isInstance).findFirst().orElseThrow();
        var record = idempotencyRepository.findById(new RequestIdempotencyId("race-client", key)).orElseThrow();
        assertEquals(winner.orderId(), record.getOrderId());
        assertEquals(new OrderRequestFingerprint().hash(winner.quantity() == 2 ? first : second), record.getRequestHash());
        assertEquals(ordersBefore + 1, orderRepository.count());
        assertEquals(keysBefore + 1, idempotencyRepository.count());
        assertEquals(2, inventoryClient.calls);
        assertFalse(inventoryClient.transactionObserved);
    }

    private List<Object> race(CreateOrderRequest firstRequest, CreateOrderRequest secondRequest, String key)
            throws Exception {
        inventoryClient.simultaneousReads = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> createOrConflict(firstRequest, key));
                var second = executor.submit(() -> createOrConflict(secondRequest, key));
                return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            } finally {
                inventoryClient.simultaneousReads = null;
                executor.shutdownNow();
            }
        }
    }

    private Object createOrConflict(CreateOrderRequest request, String key) {
        try {
            return orderService.createOrder(request, "race-client", key);
        } catch (IdempotencyConflictException exception) {
            return exception;
        }
    }

    private OrderResponse createOrder(CreateOrderRequest request) {
        return orderService.createOrder(request, "test-client", UUID.randomUUID().toString());
    }

    private CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest(UUID.randomUUID(), quantity, PaymentScenario.SUCCESS);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        RecordingInventoryClient recordingInventoryClient() {
            return new RecordingInventoryClient();
        }

        @Bean
        @Primary
        OrderMapper transactionCheckingMapper() {
            OrderMapper delegate = Mappers.getMapper(OrderMapper.class);
            return order -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return delegate.toResponse(order);
            };
        }
    }

    static class RecordingInventoryClient implements InventoryClient {

        private BigDecimal price;
        private boolean active;
        private RuntimeException failure;
        private int calls;
        private boolean transactionObserved;
        private volatile CyclicBarrier simultaneousReads;

        @Override
        public InventoryProductResponse getProduct(UUID productId) {
            synchronized (this) {
                calls++;
                transactionObserved |= TransactionSynchronizationManager.isActualTransactionActive();
            }
            CyclicBarrier barrier = simultaneousReads;
            if (barrier != null) {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent test synchronization failed", exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
            return new InventoryProductResponse(productId, price, "TRY", active);
        }
    }
}
