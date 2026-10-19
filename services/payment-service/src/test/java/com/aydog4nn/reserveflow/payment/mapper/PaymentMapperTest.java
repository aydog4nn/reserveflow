package com.aydog4nn.reserveflow.payment.mapper;

import com.aydog4nn.reserveflow.payment.domain.Payment;
import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentMapperTest {

    private final PaymentMapper mapper = Mappers.getMapper(PaymentMapper.class);

    @Test
    void mapsAllResponseFields() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-19T10:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(1);
        Instant readyAt = createdAt.plusSeconds(30);
        Payment payment = new Payment(orderId, new BigDecimal("4500.00"), "TRY", PaymentScenario.DELAY);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(payment, "createdAt", createdAt);
        ReflectionTestUtils.setField(payment, "updatedAt", updatedAt);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setReadyAt(readyAt);
        payment.setFailureReason("Test reason");

        assertEquals(new PaymentResponse(paymentId, orderId, new BigDecimal("4500.00"),
                "TRY", PaymentScenario.DELAY, PaymentStatus.FAILED, readyAt,
                "Test reason", createdAt, updatedAt), mapper.toResponse(payment));
    }

    @Test
    void mapsNullToNull() {
        assertNull(mapper.toResponse(null));
    }
}
