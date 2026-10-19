package com.aydog4nn.reserveflow.payment.dto;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePaymentRequestTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void requiresEveryInputField() {
        Set<String> fields = VALIDATOR.validate(new CreatePaymentRequest(null, null, null, null))
                .stream().map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());
        assertEquals(Set.of("orderId", "amount", "currency", "scenario"), fields);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "100000000000000000"})
    void rejectsInvalidMoney(String amount) {
        assertEquals(Set.of("amount"), VALIDATOR.validate(new CreatePaymentRequest(
                UUID.randomUUID(), new BigDecimal(amount), "TRY", PaymentScenario.SUCCESS))
                .stream().map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "try", "TR", "TRY "})
    void rejectsInvalidCurrency(String currency) {
        assertEquals(Set.of("currency"), VALIDATOR.validate(new CreatePaymentRequest(
                UUID.randomUUID(), BigDecimal.ONE, currency, PaymentScenario.SUCCESS))
                .stream().map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "4500.00", "99999999999999999.99"})
    void acceptsSupportedMoney(String amount) {
        assertTrue(VALIDATOR.validate(new CreatePaymentRequest(
                UUID.randomUUID(), new BigDecimal(amount), "TRY", PaymentScenario.SUCCESS)).isEmpty());
    }
}
