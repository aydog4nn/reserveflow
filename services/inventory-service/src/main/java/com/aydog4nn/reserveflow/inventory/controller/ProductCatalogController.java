package com.aydog4nn.reserveflow.inventory.controller;

import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.service.impl.ProductCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductCatalogController {
    private final ProductCatalogService catalog;

    public ProductCatalogController(ProductCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/products")
    public List<ProductResponse> listProducts(@RequestParam(defaultValue = "50") int limit) {
        return catalog.list(limit);
    }
}
