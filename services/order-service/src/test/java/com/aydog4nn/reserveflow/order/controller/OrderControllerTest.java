package com.aydog4nn.reserveflow.order.controller;

import com.aydog4nn.reserveflow.order.client.exception.InventoryProductNotFoundException;
import com.aydog4nn.reserveflow.order.client.exception.InventoryUnavailableException;
import com.aydog4nn.reserveflow.order.domain.OrderStatus;
import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import com.aydog4nn.reserveflow.order.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.order.idempotency.IdempotencyConflictException;
import com.aydog4nn.reserveflow.order.service.abs.IOrderService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String BODY = """
            {"productId":"22222222-2222-4222-8222-222222222222","quantity":3,"paymentScenario":"SUCCESS"}
            """;
    private StubOrderService service;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = new StubOrderService();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, "local-lab"))
                .setControllerAdvice(new ApiExceptionHandler()).setValidator(validator).build();
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void forwardsRequestAndPreservesExactKey() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "Order-Key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_STOCK"))
                .andExpect(jsonPath("$.totalAmount").value(4500.0));
        assertEquals(new CreateOrderRequest(PRODUCT_ID, 3, PaymentScenario.SUCCESS), service.request);
        assertEquals("local-lab", service.client);
        assertEquals("Order-Key", service.key);
    }

    @Test
    void missingKeyDoesNotCallService() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_HEADER"));
        assertEquals(0, service.calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "LONG"})
    void rejectsInvalidKey(String key) throws Exception {
        String header = key.equals("LONG") ? "a".repeat(129) : key;
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", header)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        assertEquals(0, service.calls);
    }

    @Test
    void acceptsMaximumKeyLength() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "a".repeat(128))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"quantity\":0}", "{\"quantity\":-1}",
            "{\"productId\":\"invalid\"}", "{", 
            "{\"productId\":\"22222222-2222-4222-8222-222222222222\",\"quantity\":3,\"paymentScenario\":\"UNKNOWN\"}"})
    void rejectsInvalidBody(String body) throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0, service.calls);
    }

    @Test
    void mapsIdempotencyConflict() throws Exception {
        assertFailure(new IdempotencyConflictException(), 409, "IDEMPOTENCY_CONFLICT");
    }

    @Test
    void mapsMissingProduct() throws Exception {
        assertFailure(new InventoryProductNotFoundException(PRODUCT_ID), 404, "PRODUCT_NOT_FOUND");
    }

    @Test
    void hidesUpstreamDetails() throws Exception {
        assertFailure(new InventoryUnavailableException("private upstream details"), 503, "SERVICE_UNAVAILABLE");
    }

    @Test
    void mapsBusinessValidation() throws Exception {
        assertFailure(new IllegalArgumentException("Ürün aktif değil!"), 400, "INVALID_ORDER");
    }

    @Test
    void hidesUnexpectedDetails() throws Exception {
        service.failure = new IllegalStateException("private database details");
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("İşlem sırasında beklenmeyen bir hata oluştu."));
    }

    @Test
    void unsupportedMethodIsNotInternalError() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsInvalidConfiguredClient() {
        assertThrows(IllegalArgumentException.class, () -> new OrderController(service, " "));
        assertThrows(IllegalArgumentException.class, () -> new OrderController(service, "a".repeat(65)));
    }

    private void assertFailure(RuntimeException failure, int statusCode, String code) throws Exception {
        service.failure = failure;
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code));
    }

    private static class StubOrderService implements IOrderService {
        private CreateOrderRequest request;
        private String client;
        private String key;
        private int calls;
        private RuntimeException failure;

        @Override
        public OrderResponse createOrder(CreateOrderRequest request, String clientId, String idempotencyKey) {
            calls++;
            this.request = request;
            client = clientId;
            key = idempotencyKey;
            if (failure != null) {
                throw failure;
            }
            return new OrderResponse(UUID.randomUUID(), request.productId(), request.quantity(),
                    new BigDecimal("1500.00"), new BigDecimal("4500.00"), "TRY",
                    request.paymentScenario(), OrderStatus.PENDING_STOCK,
                    null, null, null, Instant.EPOCH, Instant.EPOCH);
        }
    }
}
