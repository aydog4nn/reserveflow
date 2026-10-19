package com.aydog4nn.reserveflow.order.mapper;

import com.aydog4nn.reserveflow.order.domain.Order;
import com.aydog4nn.reserveflow.order.dto.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    OrderResponse toResponse(Order order);
}
