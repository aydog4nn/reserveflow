package com.aydog4nn.reserveflow.inventory.mapper;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.dto.response.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ProductMapper {

    @Mapping(target = "id", source = "product.id")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "unitPrice", source = "product.unitPrice")
    @Mapping(target = "currency", source = "product.currency")
    @Mapping(target = "active", source = "product.active")
    @Mapping(target = "onHand", source = "stock.onHand")
    @Mapping(target = "reserved", source = "stock.reserved")
    @Mapping(target = "available", source = "stock.available")
    ProductResponse toResponse(Product product, Stock stock);
}
