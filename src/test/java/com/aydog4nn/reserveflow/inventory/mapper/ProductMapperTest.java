package com.aydog4nn.reserveflow.inventory.mapper;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductMapperTest {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void mapsProductAndStockToResponse() {
        Product product = new Product("DEMO-001", "Keyboard", new BigDecimal("1500.00"), "TRY");
        UUID productId = UUID.randomUUID();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setActive(false);
        Stock stock = new Stock(product, 10);
        stock.setReserved(3);

        ProductResponse response = mapper.toResponse(product, stock);

        assertEquals(new ProductResponse(
                productId, "DEMO-001", "Keyboard", new BigDecimal("1500.00"),
                "TRY", false, 10, 3, 7
        ), response);
    }

    @Test
    void returnsNullWhenBothSourcesAreNull() {
        assertNull(mapper.toResponse(null, null));
    }
}
