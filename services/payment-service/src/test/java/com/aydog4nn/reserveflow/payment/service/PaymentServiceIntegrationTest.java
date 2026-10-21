package com.aydog4nn.reserveflow.payment.service;

import com.aydog4nn.reserveflow.payment.domain.Payment;
import com.aydog4nn.reserveflow.payment.exception.PaymentTechnicalException;
import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;
import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import com.aydog4nn.reserveflow.payment.repository.PaymentRepository;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import com.aydog4nn.reserveflow.payment.exception.PaymentNotFoundException;
import com.aydog4nn.reserveflow.payment.exception.PaymentConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"payment.scheduling.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@Testcontainers
class PaymentServiceIntegrationTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Test
    void terminalPaymentPublishesResultWithRealPaymentId() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse result = service.createPayment(request(orderId, "4500.00", PaymentScenario.SUCCESS));
        org.mockito.Mockito.verify(rabbitTemplate).convertAndSend(
                "reserveflow.events", "payment.succeeded.v1",
                new com.aydog4nn.reserveflow.payment.messaging.dto.PaymentResultEvent(
                        orderId, result.paymentId(), new BigDecimal("4500.00"), "TRY",
                        com.aydog4nn.reserveflow.payment.messaging.dto.PaymentResultEvent.Outcome.SUCCEEDED, null));
        assertTrue(payments.findById(result.paymentId()).isPresent());
    }

    @Test
    void delayPublishesOnlyWhenWorkerCompletesPayment() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse pending = service.createPayment(request(orderId, "10.00", PaymentScenario.DELAY));
        org.mockito.Mockito.verifyNoInteractions(rabbitTemplate);
        Payment payment = payments.findById(pending.paymentId()).orElseThrow();
        payment.setReadyAt(Instant.now().minusSeconds(1));
        payments.saveAndFlush(payment);
        assertEquals(1, service.completeDuePayments());
        org.mockito.Mockito.verify(rabbitTemplate).convertAndSend(
                "reserveflow.events", "payment.succeeded.v1",
                new com.aydog4nn.reserveflow.payment.messaging.dto.PaymentResultEvent(
                        orderId, pending.paymentId(), new BigDecimal("10.00"), "TRY",
                        com.aydog4nn.reserveflow.payment.messaging.dto.PaymentResultEvent.Outcome.SUCCEEDED, null));
        assertEquals(0, service.completeDuePayments());
        org.mockito.Mockito.verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void declinePublishesFailedResultButTechnicalFailurePublishesNothing() {
        PaymentResponse declined = service.createPayment(request(UUID.randomUUID(), "10", PaymentScenario.DECLINE));
        org.mockito.Mockito.verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("reserveflow.events"),
                org.mockito.ArgumentMatchers.eq("payment.failed.v1"),
                org.mockito.ArgumentMatchers.any(Object.class));
        org.mockito.Mockito.clearInvocations(rabbitTemplate);
        assertThrows(PaymentTechnicalException.class,
                () -> service.createPayment(request(UUID.randomUUID(), "10", PaymentScenario.TECHNICAL_FAILURE)));
        org.mockito.Mockito.verifyNoInteractions(rabbitTemplate);
        assertEquals(PaymentStatus.FAILED, declined.status());
    }

    @Autowired
    private com.aydog4nn.reserveflow.payment.messaging.consumer.PaymentCommandConsumer consumer;

    @Test
    void consumerCreatesPaymentAndReplayKeepsSinglePayment() {
        UUID orderId = UUID.randomUUID();
        var command = new com.aydog4nn.reserveflow.payment.messaging.dto.RequestPaymentCommand(
                orderId, new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS);
        consumer.receive(command);
        var first = payments.findByOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, first.getStatus());
        consumer.receive(command);
        assertEquals(first.getId(), payments.findByOrderId(orderId).orElseThrow().getId());
        assertEquals(1, payments.count());
    }

    @Test
    void invalidConsumerInputAndTechnicalFailureLeaveNoPayment() {
        var invalid = new com.aydog4nn.reserveflow.payment.messaging.dto.RequestPaymentCommand(
                UUID.randomUUID(), new BigDecimal("-1"), "TRY", PaymentScenario.SUCCESS);
        assertThrows(jakarta.validation.ConstraintViolationException.class, () -> consumer.receive(invalid));
        var technical = new com.aydog4nn.reserveflow.payment.messaging.dto.RequestPaymentCommand(
                UUID.randomUUID(), BigDecimal.TEN, "TRY", PaymentScenario.TECHNICAL_FAILURE);
        assertThrows(PaymentTechnicalException.class, () -> consumer.receive(technical));
        assertEquals(0, payments.count());
    }

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("payment_service_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IPaymentService service;

    @Autowired
    private PaymentRepository payments;

    @BeforeEach
    void clearPayments() {
        payments.deleteAllInBatch();
        org.mockito.Mockito.clearInvocations(rabbitTemplate);
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void resultIsNotPublishedBeforeCommit() {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            service.createPayment(request(UUID.randomUUID(), "10", PaymentScenario.SUCCESS));
            org.mockito.Mockito.verifyNoInteractions(rabbitTemplate);
        });
        org.mockito.Mockito.verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("reserveflow.events"),
                org.mockito.ArgumentMatchers.eq("payment.succeeded.v1"),
                org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void rolledBackPaymentNeverPublishesSuccess() {
        UUID orderId = UUID.randomUUID();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            service.createPayment(request(orderId, "10", PaymentScenario.SUCCESS));
            status.setRollbackOnly();
        });
        assertTrue(payments.findByOrderId(orderId).isEmpty());
        org.mockito.Mockito.verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void successIsPersistedAndSameRequestReplaysWithEquivalentMoneyScale() {
        UUID orderId = UUID.randomUUID();
        PaymentResponse first = service.createPayment(request(orderId, "4500.00", PaymentScenario.SUCCESS));
        PaymentResponse replay = service.createPayment(request(orderId, "4500.0", PaymentScenario.SUCCESS));

        assertEquals(PaymentStatus.SUCCEEDED, first.status());
        assertEquals(first.paymentId(), replay.paymentId());
        assertEquals(1, payments.count());
        assertEquals(PaymentStatus.SUCCEEDED, payments.findById(first.paymentId()).orElseThrow().getStatus());
    }

    @Test
    void declineIsBusinessResultNotException() {
        PaymentResponse response = service.createPayment(request(UUID.randomUUID(), "10", PaymentScenario.DECLINE));
        assertEquals(PaymentStatus.FAILED, response.status());
        assertNotNull(response.failureReason());
        assertEquals(PaymentStatus.FAILED, payments.findById(response.paymentId()).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "currency", "scenario"})
    void conflictingRepeatDoesNotOverwritePayment(String changedField) {
        UUID orderId = UUID.randomUUID();
        PaymentResponse first = service.createPayment(request(orderId, "10", PaymentScenario.SUCCESS));
        CreatePaymentRequest conflicting = new CreatePaymentRequest(orderId,
                new BigDecimal(changedField.equals("amount") ? "11" : "10"),
                changedField.equals("currency") ? "USD" : "TRY",
                changedField.equals("scenario") ? PaymentScenario.DECLINE : PaymentScenario.SUCCESS);

        assertThrows(PaymentConflictException.class, () -> service.createPayment(conflicting));
        assertEquals(1, payments.count());
        Payment saved = payments.findById(first.paymentId()).orElseThrow();
        assertEquals(new BigDecimal("10.00"), saved.getAmount());
        assertEquals("TRY", saved.getCurrency());
        assertEquals(PaymentScenario.SUCCESS, saved.getScenario());
    }

    @Test
    void delayStaysPendingUntilDueThenCompletesOnceWithoutExplicitSave() {
        Instant before = Instant.now();
        UUID orderId = UUID.randomUUID();
        PaymentResponse pending = service.createPayment(request(orderId, "10", PaymentScenario.DELAY));
        assertEquals(PaymentStatus.PENDING, pending.status());
        assertFalse(pending.readyAt().isBefore(before.plusSeconds(10)));
        assertFalse(pending.readyAt().isAfter(Instant.now().plusSeconds(10)));
        assertEquals(0, service.completeDuePayments());
        PaymentResponse replay = service.createPayment(request(orderId, "10", PaymentScenario.DELAY));
        assertEquals(pending.paymentId(), replay.paymentId());
        assertEquals(pending.readyAt().toEpochMilli(), replay.readyAt().toEpochMilli());

        Payment payment = payments.findById(pending.paymentId()).orElseThrow();
        payment.setReadyAt(Instant.now().minusSeconds(1));
        payments.saveAndFlush(payment);
        assertEquals(1, service.completeDuePayments());
        Payment completed = payments.findById(pending.paymentId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, completed.getStatus());
        assertEquals(PaymentStatus.SUCCEEDED, service.getPaymentById(pending.paymentId()).status());
        assertEquals(0, service.completeDuePayments());
        assertEquals(PaymentStatus.SUCCEEDED,
                service.createPayment(request(orderId, "10", PaymentScenario.DELAY)).status());
    }

    @Test
    void completionIgnoresFutureMissingScheduleAndOtherScenarios() {
        Payment future = new Payment(UUID.randomUUID(), BigDecimal.ONE, "TRY", PaymentScenario.DELAY);
        future.setReadyAt(Instant.now().plusSeconds(3600));
        payments.saveAndFlush(future);
        payments.saveAndFlush(new Payment(UUID.randomUUID(), BigDecimal.ONE, "TRY", PaymentScenario.DELAY));
        Payment otherScenario = new Payment(UUID.randomUUID(), BigDecimal.ONE, "TRY", PaymentScenario.SUCCESS);
        otherScenario.setReadyAt(Instant.now().minusSeconds(60));
        payments.saveAndFlush(otherScenario);
        assertEquals(0, service.completeDuePayments());
        assertTrue(payments.findAll().stream().allMatch(payment -> payment.getStatus() == PaymentStatus.PENDING));
    }

    @Test
    void completionProcessesOldestHundredThenRemaining() {
        Instant base = Instant.now().minusSeconds(1000);
        UUID oldestId = null;
        UUID newestId = null;
        for (int index = 0; index < 101; index++) {
            Payment payment = new Payment(UUID.randomUUID(), BigDecimal.ONE, "TRY", PaymentScenario.DELAY);
            payment.setReadyAt(base.plusSeconds(index));
            Payment saved = payments.saveAndFlush(payment);
            if (index == 0) {
                oldestId = saved.getId();
            }
            newestId = saved.getId();
        }
        assertEquals(100, service.completeDuePayments());
        assertEquals(PaymentStatus.SUCCEEDED, payments.findById(oldestId).orElseThrow().getStatus());
        assertEquals(PaymentStatus.PENDING, payments.findById(newestId).orElseThrow().getStatus());
        assertEquals(1, service.completeDuePayments());
        assertEquals(0, service.completeDuePayments());
    }

    @Test
    void technicalFailureDoesNotCreatePayment() {
        assertThrows(PaymentTechnicalException.class,
                () -> service.createPayment(request(UUID.randomUUID(), "10", PaymentScenario.TECHNICAL_FAILURE)));
        assertEquals(0, payments.count());
    }

    private CreatePaymentRequest request(UUID orderId, String amount, PaymentScenario scenario) {
        return new CreatePaymentRequest(orderId, new BigDecimal(amount), "TRY", scenario);
    }

    @Test
    void readsPersistedPaymentAndRejectsMissingId() {
        PaymentResponse created = service.createPayment(request(UUID.randomUUID(), "10", PaymentScenario.SUCCESS));
        PaymentResponse loaded = service.getPaymentById(created.paymentId());
        assertEquals(created.paymentId(), loaded.paymentId());
        assertEquals(created.orderId(), loaded.orderId());
        assertEquals(PaymentStatus.SUCCEEDED, loaded.status());
        assertThrows(PaymentNotFoundException.class, () -> service.getPaymentById(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.getPaymentById(null));
    }
}
