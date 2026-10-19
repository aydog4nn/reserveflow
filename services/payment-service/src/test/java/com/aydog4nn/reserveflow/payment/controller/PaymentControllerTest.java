package com.aydog4nn.reserveflow.payment.controller;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;
import com.aydog4nn.reserveflow.payment.dto.request.CreatePaymentRequest;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import com.aydog4nn.reserveflow.payment.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.payment.exception.PaymentConflictException;
import com.aydog4nn.reserveflow.payment.exception.PaymentNotFoundException;
import com.aydog4nn.reserveflow.payment.exception.PaymentTechnicalException;
import com.aydog4nn.reserveflow.payment.service.abs.IPaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PaymentControllerTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String BODY = """
            {"orderId":"11111111-1111-4111-8111-111111111111","amount":4500.00,"currency":"TRY","scenario":"SUCCESS"}
            """;

    private StubPaymentService service;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = new StubPaymentService();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void createsPaymentAndForwardsRequest() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value(4500.0));
        assertEquals(new CreatePaymentRequest(ORDER_ID, new BigDecimal("4500.00"),
                "TRY", PaymentScenario.SUCCESS), service.request);
    }

    @Test
    void readsPaymentByPaymentId() throws Exception {
        mvc.perform(get("/api/v1/payments/{paymentId}", PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()));
        assertEquals(PAYMENT_ID, service.readId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"orderId\":null}", 
            "{\"orderId\":\"11111111-1111-4111-8111-111111111111\",\"amount\":-1,\"currency\":\"TRY\",\"scenario\":\"SUCCESS\"}",
            "{\"orderId\":\"11111111-1111-4111-8111-111111111111\",\"amount\":1.001,\"currency\":\"TRY\",\"scenario\":\"SUCCESS\"}",
            "{\"orderId\":\"11111111-1111-4111-8111-111111111111\",\"amount\":1,\"currency\":\"try\",\"scenario\":\"SUCCESS\"}"})
    void invalidFieldsDoNotReachService(String body) throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertEquals(0, service.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"orderId\":\"invalid\"}",
            "{\"orderId\":\"11111111-1111-4111-8111-111111111111\",\"amount\":1,\"currency\":\"TRY\",\"scenario\":\"UNKNOWN\"}"})
    void invalidJsonDoesNotReachService(String body) throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        assertEquals(0, service.calls);
    }

    @Test
    void invalidPathIdDoesNotReachService() throws Exception {
        mvc.perform(get("/api/v1/payments/invalid")).andExpect(status().isBadRequest());
        assertEquals(0, service.calls);
    }

    @Test
    void mapsMissingPayment() throws Exception {
        service.failure = new PaymentNotFoundException(PAYMENT_ID);
        mvc.perform(get("/api/v1/payments/{paymentId}", PAYMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void mapsConflict() throws Exception {
        assertFailure(new PaymentConflictException(), 409, "PAYMENT_CONFLICT");
    }

    @Test
    void mapsTechnicalFailure() throws Exception {
        assertFailure(new PaymentTechnicalException(), 503, "PAYMENT_TECHNICAL_FAILURE");
    }

    @Test
    void mapsDatabaseOutageWithoutExposingDetails() throws Exception {
        assertFailure(new DataAccessResourceFailureException("private connection details"), 503, "SERVICE_UNAVAILABLE");
    }

    @Test
    void hidesUnexpectedDetails() throws Exception {
        service.failure = new IllegalStateException("private database details");
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("İşlem sırasında beklenmeyen bir hata oluştu."));
    }

    @Test
    void preservesMethodNotAllowed() throws Exception {
        mvc.perform(get("/api/v1/payments")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0, service.calls);
    }

    private void assertFailure(RuntimeException failure, int statusCode, String code) throws Exception {
        service.failure = failure;
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    private static class StubPaymentService implements IPaymentService {
        private CreatePaymentRequest request;
        private UUID readId;
        private int calls;
        private RuntimeException failure;

        @Override
        public PaymentResponse createPayment(CreatePaymentRequest request) {
            calls++;
            this.request = request;
            return response();
        }

        @Override
        public PaymentResponse getPaymentById(UUID paymentId) {
            calls++;
            readId = paymentId;
            return response();
        }

        @Override
        public int completeDuePayments() {
            throw new UnsupportedOperationException();
        }

        private PaymentResponse response() {
            if (failure != null) {
                throw failure;
            }
            return new PaymentResponse(PAYMENT_ID, ORDER_ID, new BigDecimal("4500.00"),
                    "TRY", PaymentScenario.SUCCESS, PaymentStatus.SUCCEEDED,
                    null, null, Instant.EPOCH, Instant.EPOCH);
        }
    }
}
