package com.aydog4nn.reserveflow.inventory.controller;

import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.inventory.exception.ProductNotFoundException;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String VALID_REQUEST = """
            {"sku":"DEMO-001","name":"Keyboard","unitPrice":1500.00,"currency":"TRY","initialStock":10}
            """;

    private StubProductService productService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = new StubProductService();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void createsProductAndReturnsResponse() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.sku").value("DEMO-001"))
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.unitPrice").value(1500.0))
                .andExpect(jsonPath("$.currency").value("TRY"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(10));

        assertEquals(new CreateProductRequest(
                "DEMO-001", "Keyboard", new BigDecimal("1500.00"), "TRY", 10
        ), productService.lastRequest);
        assertEquals(1, productService.invocationCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"sku\":\" \",\"name\":\"Keyboard\",\"unitPrice\":1,\"currency\":\"TRY\",\"initialStock\":0}",
            "{\"sku\":\"DEMO-001\",\"name\":\" \",\"unitPrice\":1,\"currency\":\"TRY\",\"initialStock\":0}",
            "{\"sku\":\"DEMO-001\",\"name\":\"Keyboard\",\"unitPrice\":-1,\"currency\":\"TRY\",\"initialStock\":0}",
            "{\"sku\":\"DEMO-001\",\"name\":\"Keyboard\",\"unitPrice\":1.001,\"currency\":\"TRY\",\"initialStock\":0}",
            "{\"sku\":\"DEMO-001\",\"name\":\"Keyboard\",\"unitPrice\":1,\"currency\":\"try\",\"initialStock\":0}",
            "{\"sku\":\"DEMO-001\",\"name\":\"Keyboard\",\"unitPrice\":1,\"currency\":\"TRY\",\"initialStock\":-1}",
            "{\"sku\":\"DEMO-001\",\"name\":\"Keyboard\",\"unitPrice\":1,\"currency\":\"TRY\"}"
    })
    void rejectsInvalidFieldsBeforeCallingService(String body) throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isMap());
        assertEquals(0, productService.invocationCount);
    }

    @Test
    void acceptsZeroPriceAndInitialStock() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("1500.00", "0").replace("\"initialStock\":10", "\"initialStock\":0")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onHand").value(0));
        assertEquals(1, productService.invocationCount);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        assertEquals(0, productService.invocationCount);
    }

    @Test
    void returnsConflictWithoutDatabaseDetails() throws Exception {
        productService.failure = new DataIntegrityViolationException("private database details");
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    void returnsProductAndStockById() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.sku").value("DEMO-001"))
                .andExpect(jsonPath("$.unitPrice").value(1500.0))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.reserved").value(3))
                .andExpect(jsonPath("$.available").value(7))
                .andExpect(jsonPath("$.product").doesNotExist());
        assertEquals(PRODUCT_ID, productService.lastReadId);
        assertEquals(1, productService.invocationCount);
    }

    @Test
    void returnsNotFoundForMissingProduct() throws Exception {
        productService.failure = new ProductNotFoundException(PRODUCT_ID);
        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidProductIdBeforeCallingService() throws Exception {
        mockMvc.perform(get("/api/v1/products/not-a-uuid"))
                .andExpect(status().isBadRequest());
        assertEquals(0, productService.invocationCount);
    }

    @Test
    void returnsGenericServerErrorForMissingStock() throws Exception {
        productService.failure = new IllegalStateException("Private invariant details");
        mockMvc.perform(get("/api/v1/products/{id}", PRODUCT_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("İşlem sırasında beklenmeyen bir hata oluştu."));
    }

    private static class StubProductService implements IProductService {

        @Override
        public ProductResponse getProductById(UUID productId) {
            invocationCount++;
            lastReadId = productId;
            if (failure != null) {
                throw failure;
            }
            return new ProductResponse(
                    productId, "DEMO-001", "Keyboard", new BigDecimal("1500.00"),
                    "TRY", true, 10, 3, 7
            );
        }

        private UUID lastReadId;

        private CreateProductRequest lastRequest;
        private int invocationCount;
        private RuntimeException failure;

        @Override
        public ProductResponse createProduct(CreateProductRequest request) {
            invocationCount++;
            lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return new ProductResponse(
                    PRODUCT_ID, request.sku(), request.name(), request.unitPrice(),
                    request.currency(), true, request.initialStock(), 0, request.initialStock()
            );
        }
    }
}
