package com.aydog4nn.reserveflow.inventory.service.impl;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import com.aydog4nn.reserveflow.inventory.mapper.ProductMapper;
import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IProductService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

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
}
