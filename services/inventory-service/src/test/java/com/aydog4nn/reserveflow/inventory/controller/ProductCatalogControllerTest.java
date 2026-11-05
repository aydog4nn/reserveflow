package com.aydog4nn.reserveflow.inventory.controller;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.exception.ApiExceptionHandler;
import com.aydog4nn.reserveflow.inventory.mapper.ProductMapper;
import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.impl.ProductCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductCatalogControllerTest {
    private ProductRepository products;
    private StockRepository stocks;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        stocks = mock(StockRepository.class);
        var catalog = new ProductCatalogService(products, stocks, Mappers.getMapper(ProductMapper.class));
        mvc = MockMvcBuilders.standaloneSetup(new ProductCatalogController(catalog))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsProductWithStockUsingABoundedQuery() throws Exception {
        UUID id = UUID.randomUUID();
        var product = new Product("DEMO-KEY", "Klavye", new BigDecimal("1500.00"), "TRY");
        ReflectionTestUtils.setField(product, "id", id);
        var stock = new Stock(product, 10);
        ReflectionTestUtils.setField(stock, "productId", id);
        when(products.findAll(any(Pageable.class))).thenAnswer(call -> {
            Pageable page = call.getArgument(0);
            assertEquals(20, page.getPageSize());
            assertEquals(0, page.getPageNumber());
            return new PageImpl<>(List.of(product));
        });
        when(stocks.findAllById(List.of(id))).thenReturn(List.of(stock));
        mvc.perform(get("/api/v1/products?limit=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].available").value(10));
        verify(stocks).findAllById(List.of(id));
    }

    @Test
    void emptyCatalogDoesNotFetchStocks() throws Exception {
        when(products.findAll(any(Pageable.class))).thenReturn(Page.empty());
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verifyNoInteractions(stocks);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, 1000000})
    void invalidLimitIsRejectedBeforeDatabaseAccess(int limit) throws Exception {
        mvc.perform(get("/api/v1/products?limit=" + limit)).andExpect(status().isBadRequest());
        verifyNoInteractions(products, stocks);
    }
}
