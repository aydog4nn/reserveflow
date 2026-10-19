package com.aydog4nn.reserveflow.inventory.service.impl;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.exception.ProductNotFoundException;
import com.aydog4nn.reserveflow.inventory.mapper.ProductMapper;
import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service

public class ProductServiceImpl implements IProductService {



    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductRepository productRepository, StockRepository stockRepository,
                              ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.productMapper = productMapper;
    }


    private Product createAndSaveProduct(CreateProductRequest request) {
        Product product = new Product(
                request.sku(),
                request.name(),
                request.unitPrice(),
                request.currency()
        );

        return productRepository.save(product);
    }

    private Stock createAndSaveStock(Product product, int initialStock) {
        Stock stock = new Stock(product, initialStock);

        return stockRepository.save(stock);
    }

    @Transactional
    @Override
    public ProductResponse createProduct(CreateProductRequest request) {


        Product product = createAndSaveProduct(request);
        Stock stock = createAndSaveStock(product, request.initialStock());

        return productMapper.toResponse(product, stock);

    }


    private void validateProductId(UUID productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Ürün kimliği null olamaz!");
        }
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private Stock findStock(UUID productId) {
        return stockRepository.findById(productId)
                .orElseThrow(() -> new IllegalStateException("Ürünün stok kaydı bulunamadı."));
    }

    @Transactional(readOnly = true)
    @Override
    public ProductResponse getProductById(UUID productId) {
        validateProductId(productId);
        Product product = findProduct(productId);
        Stock stock = findStock(product.getId());

        return productMapper.toResponse(product, stock);
    }


}
