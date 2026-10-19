package com.aydog4nn.reserveflow.inventory.service.abs;

import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;

import java.util.UUID;

public interface IProductService {


    public ProductResponse createProduct(CreateProductRequest request);

    public ProductResponse getProductById(UUID productId);
}
