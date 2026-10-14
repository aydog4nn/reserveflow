package com.aydog4nn.reserveflow.inventory.controller;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationControllerTest {

    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESERVATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String VALID_REQUEST = """
            {"orderId":"22222222-2222-2222-2222-222222222222",
             "productId":"11111111-1111-1111-1111-111111111111","quantity":2}
            """;

    private StubInventoryService inventoryService;
    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        inventoryService = new StubInventoryService();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(inventoryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (validator != null) {
            validator.close();
        }
    }

    @Test
    void returnsResponseDtoWithoutProductEntity() throws Exception {
        inventoryService.result = reservation();

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()))
                .andExpect(jsonPath("$.orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.product").doesNotExist());
        assertEquals(1, inventoryService.invocationCount);
        assertEquals(ORDER_ID, inventoryService.lastOrderId);
        assertEquals(PRODUCT_ID, inventoryService.lastProductId);
        assertEquals(2, inventoryService.lastQuantity);
    }

    @Test
    void repeatedRequestReturnsSameReservation() throws Exception {
        inventoryService.result = reservation();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(RESERVATION_ID.toString()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"orderId\":null,\"productId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":2}",
            "{\"orderId\":\"22222222-2222-2222-2222-222222222222\",\"quantity\":2}",
            "{\"orderId\":\"22222222-2222-2222-2222-222222222222\",\"productId\":\"11111111-1111-1111-1111-111111111111\"}",
            "{\"orderId\":\"22222222-2222-2222-2222-222222222222\",\"productId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":0}",
            "{\"orderId\":\"22222222-2222-2222-2222-222222222222\",\"productId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":-1}"
    })
    void rejectsInvalidFieldsBeforeCallingService(String body) throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isMap());
        assertEquals(0, inventoryService.invocationCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{",
            "null",
            "{\"orderId\":\"invalid\",\"productId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":2}",
            "{\"orderId\":\"22222222-2222-2222-2222-222222222222\",\"productId\":\"11111111-1111-1111-1111-111111111111\",\"quantity\":\"invalid\"}"
    })
    void rejectsUnreadableRequest(String body) throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        assertEquals(0, inventoryService.invocationCount);
    }

    @Test
    void reportsServiceValidationAsBadRequest() throws Exception {
        inventoryService.failure = new IllegalArgumentException("Yetersiz stok.");

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Yetersiz stok."))
                .andExpect(jsonPath("$.code").value("INVALID_RESERVATION"));
    }

    @Test
    void reportsConflictWithoutDatabaseDetails() throws Exception {
        inventoryService.failure = new DataIntegrityViolationException("private database details");

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(
                        "İşlem mevcut kayıtlarla çakışıyor. Sipariş kimliğini ve rezervasyon bilgilerini kontrol edin."
                ));
    }

    @Test
    void reportsLockFailureAsUnavailable() throws Exception {
        inventoryService.failure = new CannotAcquireLockException("private database details");

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void doesNotLeakUnexpectedException() throws Exception {
        inventoryService.failure = new IllegalStateException("private internal details");

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("İşlem sırasında beklenmeyen bir hata oluştu."));
    }

    @Test
    void preservesMethodNotAllowedStatus() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isMethodNotAllowed());
        assertEquals(0, inventoryService.invocationCount);
    }

    private static class StubInventoryService implements IInventoryService {

        private Reservation result;
        private RuntimeException failure;
        private int invocationCount;
        private UUID lastOrderId;
        private UUID lastProductId;
        private int lastQuantity;

        @Override
        public Reservation reserveStock(UUID orderId, UUID productId, int quantity) {
            invocationCount++;
            lastOrderId = orderId;
            lastProductId = productId;
            lastQuantity = quantity;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private Reservation reservation() {
        Product product = new Product("HEADPHONE-001", "Kulaklık", new BigDecimal("1499.90"), "TRY");
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        Reservation reservation = new Reservation(ORDER_ID, product, 2);
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
        Instant createdAt = Instant.parse("2026-10-14T12:00:00Z");
        ReflectionTestUtils.setField(reservation, "createdAt", createdAt);
        ReflectionTestUtils.setField(reservation, "updatedAt", createdAt);
        return reservation;
    }
}
