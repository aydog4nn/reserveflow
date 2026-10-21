package com.aydog4nn.reserveflow.payment.messaging;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.exception.PaymentTechnicalException;
import com.aydog4nn.reserveflow.payment.messaging.consumer.PaymentCommandConsumer;
import com.aydog4nn.reserveflow.payment.messaging.dto.RequestPaymentCommand;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentCommandConsumerTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void delegatesValidatedFieldsToExistingService() {
        IPaymentService service = mock(IPaymentService.class);
        UUID orderId = UUID.randomUUID();
        new PaymentCommandConsumer(service, FACTORY.getValidator()).receive(
                new RequestPaymentCommand(orderId, new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS));
        verify(service).createPayment(new CreatePaymentRequest(
                orderId, new BigDecimal("4500.00"), "TRY", PaymentScenario.SUCCESS));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-id", "negative", "precision", "currency", "null-scenario"})
    void rejectsInvalidMessageBeforeService(String invalidField) {
        IPaymentService service = mock(IPaymentService.class);
        RequestPaymentCommand command = new RequestPaymentCommand(
                invalidField.equals("null-id") ? null : UUID.randomUUID(),
                new BigDecimal(invalidField.equals("negative") ? "-1" :
                        invalidField.equals("precision") ? "1.001" : "10"),
                invalidField.equals("currency") ? "try" : "TRY",
                invalidField.equals("null-scenario") ? null : PaymentScenario.SUCCESS);
        assertThrows(ConstraintViolationException.class,
                () -> new PaymentCommandConsumer(service, FACTORY.getValidator()).receive(command));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNullMessage() {
        IPaymentService service = mock(IPaymentService.class);
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentCommandConsumer(service, FACTORY.getValidator()).receive(null));
        verifyNoInteractions(service);
    }

    @Test
    void propagatesTechnicalFailureInsteadOfTreatingItAsDecline() {
        IPaymentService service = mock(IPaymentService.class);
        PaymentTechnicalException failure = new PaymentTechnicalException();
        when(service.createPayment(any())).thenThrow(failure);
        RequestPaymentCommand command = new RequestPaymentCommand(
                UUID.randomUUID(), BigDecimal.TEN, "TRY", PaymentScenario.TECHNICAL_FAILURE);
        assertSame(failure, assertThrows(PaymentTechnicalException.class,
                () -> new PaymentCommandConsumer(service, FACTORY.getValidator()).receive(command)));
    }
}
