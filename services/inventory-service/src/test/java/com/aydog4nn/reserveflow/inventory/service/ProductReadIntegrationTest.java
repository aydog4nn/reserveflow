package com.aydog4nn.reserveflow.inventory.service;

import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.exception.ProductNotFoundException;
import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class ProductReadIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("inventory_read_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IProductService productService;
    @Autowired
    private IInventoryService inventoryService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockRepository stockRepository;

    @Test
    void readsProductPriceAndStockWithoutChangingThem() {
        ProductResponse created = createProduct();
        inventoryService.reserveStock(UUID.randomUUID(), created.id(), 3);
        var before = stockRepository.findById(created.id()).orElseThrow();

        ProductResponse response = productService.getProductById(created.id());

        assertEquals(created.id(), response.id());
        assertEquals(created.sku(), response.sku());
        assertEquals("Keyboard", response.name());
        assertEquals(new BigDecimal("1500.00"), response.unitPrice());
        assertEquals("TRY", response.currency());
        assertTrue(response.active());
        assertEquals(10, response.onHand());
        assertEquals(3, response.reserved());
        assertEquals(7, response.available());
        var after = stockRepository.findById(created.id()).orElseThrow();
        assertEquals(before.getOnHand(), after.getOnHand());
        assertEquals(before.getReserved(), after.getReserved());
        assertEquals(before.getVersion(), after.getVersion());
    }

    @Test
    void rejectsMissingProduct() {
        assertThrows(ProductNotFoundException.class,
                () -> productService.getProductById(UUID.randomUUID()));
    }

    @Test
    void rejectsNullId() {
        assertThrows(IllegalArgumentException.class, () -> productService.getProductById(null));
    }

    @Test
    void treatsMissingStockAsInconsistentDataNotMissingProduct() {
        ProductResponse product = createProduct();
        stockRepository.deleteById(product.id());
        assertTrue(productRepository.existsById(product.id()));
        assertThrows(IllegalStateException.class, () -> productService.getProductById(product.id()));
    }

    private ProductResponse createProduct() {
        return productService.createProduct(new CreateProductRequest(
                "READ-" + UUID.randomUUID(), "Keyboard", new BigDecimal("1500.00"), "TRY", 10
        ));
    }
}
