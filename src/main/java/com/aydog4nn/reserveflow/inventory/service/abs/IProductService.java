package com.aydog4nn.reserveflow.inventory.service.abs;

import com.aydog4nn.reserveflow.inventory.dto.request.CreateProductRequest;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;

public interface IProductService {


    public ProductResponse createProduct(CreateProductRequest request);

}
