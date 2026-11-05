package com.aydog4nn.reserveflow.inventory.service.impl;

import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.mapper.ProductMapper;
import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductCatalogService {
    private final ProductRepository products;
    private final StockRepository stocks;
    private final ProductMapper mapper;

    public ProductCatalogService(ProductRepository products, StockRepository stocks, ProductMapper mapper) {
        this.products = products;
        this.stocks = stocks;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Ürün listeleme sınırı 1 ile 100 arasında olmalı.");
        }
        var page = products.findAll(PageRequest.of(0, limit, Sort.by("name", "id")));
        if (page.isEmpty()) return List.of();
        var stockById = stocks.findAllById(page.stream().map(product -> product.getId()).toList())
                .stream().collect(Collectors.toMap(Stock::getProductId, Function.identity()));
        return page.stream().map(product -> {
            Stock stock = stockById.get(product.getId());
            if (stock == null) throw new IllegalStateException("Ürünün stok kaydı bulunamadı.");
            return mapper.toResponse(product, stock);
        }).toList();
    }
}
