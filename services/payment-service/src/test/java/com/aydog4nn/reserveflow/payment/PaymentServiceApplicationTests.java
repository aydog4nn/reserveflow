package com.aydog4nn.reserveflow.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.aydog4nn.reserveflow.payment.domain.Payment;
import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;
import com.aydog4nn.reserveflow.payment.repository.PaymentRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"payment.scheduling.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@Testcontainers
class PaymentServiceApplicationTests {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Validator validator;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("payment_test")
            .withUsername("payment_test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoads() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class));
    }

    @Test
    void persistsPendingPaymentWithoutCrossServiceEntity() {
        UUID orderId = UUID.randomUUID();
        Payment saved = payments.saveAndFlush(new Payment(
                orderId, new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS));
        Payment loaded = payments.findByOrderId(orderId).orElseThrow();
        assertNotNull(saved.getId());
        assertEquals(saved.getId(), loaded.getId());
        assertEquals(new BigDecimal("4500.00"), loaded.getAmount());
        assertEquals("TRY", loaded.getCurrency());
        assertEquals(PaymentScenario.SUCCESS, loaded.getScenario());
        assertEquals(PaymentStatus.PENDING, loaded.getStatus());
        assertNotNull(loaded.getCreatedAt());
        assertEquals(loaded.getCreatedAt(), loaded.getUpdatedAt());
        assertEquals(0L, loaded.getVersion());
        assertNull(loaded.getReadyAt());
        assertNull(loaded.getFailureReason());
    }

    @Test
    void rejectsSecondPaymentForSameOrderWithoutOverwritingFirst() {
        UUID orderId = UUID.randomUUID();
        Payment first = payments.saveAndFlush(new Payment(
                orderId, BigDecimal.TEN, "TRY", PaymentScenario.SUCCESS));
        assertThrows(DataIntegrityViolationException.class, () -> payments.saveAndFlush(
                new Payment(orderId, BigDecimal.ONE, "TRY", PaymentScenario.DECLINE)));
        assertEquals(first.getId(), payments.findByOrderId(orderId).orElseThrow().getId());
    }

    @Test
    void storesDelayScheduleAndTracksUpdates() {
        Payment saved = payments.saveAndFlush(new Payment(
                UUID.randomUUID(), BigDecimal.ZERO, "TRY", PaymentScenario.DELAY));
        Instant readyAt = Instant.parse("2026-10-19T12:00:00Z");
        saved.setReadyAt(readyAt);
        saved.setFailureReason("Test metadata");
        payments.saveAndFlush(saved);
        Payment loaded = payments.findById(saved.getId()).orElseThrow();
        assertEquals(readyAt, loaded.getReadyAt());
        assertEquals(PaymentStatus.PENDING, loaded.getStatus());
        assertEquals("Test metadata", loaded.getFailureReason());
        assertEquals(1L, loaded.getVersion());
        assertFalse(loaded.getUpdatedAt().isBefore(loaded.getCreatedAt()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-1, 'TRY', 'SUCCESS', 'PENDING'",
            "1, 'try', 'SUCCESS', 'PENDING'",
            "1, 'TRY', 'UNKNOWN', 'PENDING'",
            "1, 'TRY', 'SUCCESS', 'UNKNOWN'"
    })
    void databaseRejectsInvalidValues(String values) {
        String sql = "INSERT INTO payments (id, order_id, amount, currency, scenario, status, created_at, updated_at)"
                + " VALUES (?, ?, " + values + ", now(), now())";
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update(sql, UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void validatesMoneyAndRequiredFieldsBeforePersistence() {
        assertFalse(validator.validate(new Payment(null, null, null, null)).isEmpty());
        assertFalse(validator.validate(new Payment(
                UUID.randomUUID(), new BigDecimal("1.001"), "TRY", PaymentScenario.SUCCESS)).isEmpty());
        assertFalse(validator.validate(new Payment(
                UUID.randomUUID(), new BigDecimal("100000000000000000"), "TRY", PaymentScenario.SUCCESS)).isEmpty());
        assertTrue(validator.validate(new Payment(
                UUID.randomUUID(), BigDecimal.ZERO, "TRY", PaymentScenario.SUCCESS)).isEmpty());
    }
}
