package com.aydog4nn.reserveflow.order.service;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.messaging.dto.PaymentResultEvent;
import com.aydog4nn.reserveflow.order.repository.OrderRepository;
import com.aydog4nn.reserveflow.order.service.impl.OrderPaymentService;
import com.aydog4nn.reserveflow.order.messaging.consumer.PaymentResultConsumer;
import com.aydog4nn.reserveflow.order.messaging.dto.StockActionCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.StockActionProducer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.username=test", "spring.rabbitmq.password=test"
})
@Testcontainers
class OrderPaymentServiceIntegrationTest {

    @Autowired
    private PaymentResultConsumer consumer;

    @MockitoBean
    private StockActionProducer stockActions;

    @ParameterizedTest
    @EnumSource(PaymentResultEvent.Outcome.class)
    void consumerSendsMatchingActionAfterStateCommit(PaymentResultEvent.Outcome outcome) {
        Order order = waitingOrder();
        PaymentResultEvent event = event(order, outcome);
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(event.paymentId(), orders.findById(order.getId()).orElseThrow().getPaymentId());
            return null;
        }).when(stockActions).commit(any());
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(event.paymentId(), orders.findById(order.getId()).orElseThrow().getPaymentId());
            return null;
        }).when(stockActions).release(any());
        consumer.receive(event);
        StockActionCommand command = new StockActionCommand(order.getId(), order.getReservationId());
        if (outcome == PaymentResultEvent.Outcome.SUCCEEDED) {
            verify(stockActions).commit(command);
            verify(stockActions, never()).release(any());
        } else {
            verify(stockActions).release(command);
            verify(stockActions, never()).commit(any());
        }
    }

    @Test
    void terminalDuplicateDoesNotSendStockCommand() {
        Order order = waitingOrder();
        PaymentResultEvent event = event(order, PaymentResultEvent.Outcome.SUCCEEDED);
        service.handlePaymentResult(event);
        Order completed = orders.findById(order.getId()).orElseThrow();
        completed.setStatus(OrderStatus.CONFIRMED);
        orders.saveAndFlush(completed);
        consumer.receive(event);
        verifyNoInteractions(stockActions);
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("payment_result_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private OrderPaymentService service;
    @Autowired
    private OrderRepository orders;

    @ParameterizedTest
    @EnumSource(PaymentResultEvent.Outcome.class)
    void recordsResultAndDuplicateDoesNotChangeVersion(PaymentResultEvent.Outcome outcome) {
        Order order = waitingOrder();
        PaymentResultEvent event = event(order, outcome);
        var response = service.handlePaymentResult(event);
        OrderStatus expected = outcome == PaymentResultEvent.Outcome.SUCCEEDED
                ? OrderStatus.STOCK_COMMIT_PENDING : OrderStatus.COMPENSATION_PENDING;
        assertEquals(expected, response.status());
        assertEquals(event.paymentId(), response.paymentId());
        assertEquals(event.failureReason(), response.failureReason());
        Long version = orders.findById(order.getId()).orElseThrow().getVersion();
        service.handlePaymentResult(event);
        assertEquals(version, orders.findById(order.getId()).orElseThrow().getVersion());
    }

    @Test
    void rejectsConflictingPaymentAndContradictoryOutcome() {
        Order order = waitingOrder();
        PaymentResultEvent succeeded = event(order, PaymentResultEvent.Outcome.SUCCEEDED);
        service.handlePaymentResult(succeeded);
        assertThrows(IllegalArgumentException.class,
                () -> service.handlePaymentResult(event(order, PaymentResultEvent.Outcome.SUCCEEDED)));
        assertThrows(IllegalArgumentException.class, () -> service.handlePaymentResult(
                new PaymentResultEvent(order.getId(), succeeded.paymentId(), order.getTotalAmount(),
                        "TRY", PaymentResultEvent.Outcome.FAILED, "Declined")));
        assertEquals(OrderStatus.STOCK_COMMIT_PENDING, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectsAmountCurrencyMissingOrderAndPrematureResult() {
        Order order = waitingOrder();
        assertThrows(IllegalArgumentException.class, () -> service.handlePaymentResult(
                new PaymentResultEvent(order.getId(), UUID.randomUUID(), BigDecimal.ONE,
                        "TRY", PaymentResultEvent.Outcome.SUCCEEDED, null)));
        assertThrows(IllegalArgumentException.class, () -> service.handlePaymentResult(
                new PaymentResultEvent(order.getId(), UUID.randomUUID(), order.getTotalAmount(),
                        "USD", PaymentResultEvent.Outcome.SUCCEEDED, null)));
        assertThrows(IllegalArgumentException.class, () -> service.handlePaymentResult(
                new PaymentResultEvent(UUID.randomUUID(), UUID.randomUUID(), order.getTotalAmount(),
                        "TRY", PaymentResultEvent.Outcome.SUCCEEDED, null)));
        order.setStatus(OrderStatus.PENDING_STOCK);
        orders.saveAndFlush(order);
        assertThrows(IllegalArgumentException.class,
                () -> service.handlePaymentResult(event(order, PaymentResultEvent.Outcome.SUCCEEDED)));
        assertNull(orders.findById(order.getId()).orElseThrow().getPaymentId());
    }

    @Test
    void lateDuplicateDoesNotRegressTerminalOrder() {
        Order order = waitingOrder();
        PaymentResultEvent event = event(order, PaymentResultEvent.Outcome.SUCCEEDED);
        service.handlePaymentResult(event);
        Order completed = orders.findById(order.getId()).orElseThrow();
        completed.setStatus(OrderStatus.CONFIRMED);
        orders.saveAndFlush(completed);
        service.handlePaymentResult(event);
        assertEquals(OrderStatus.CONFIRMED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    private Order waitingOrder() {
        Order order = new Order(UUID.randomUUID(), 3, new BigDecimal("1500.00"),
                new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS);
        order.setReservationId(UUID.randomUUID());
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        return orders.saveAndFlush(order);
    }

    private PaymentResultEvent event(Order order, PaymentResultEvent.Outcome outcome) {
        return new PaymentResultEvent(order.getId(), UUID.randomUUID(), order.getTotalAmount(), "TRY",
                outcome, outcome == PaymentResultEvent.Outcome.FAILED ? "Declined" : null);
    }
}
