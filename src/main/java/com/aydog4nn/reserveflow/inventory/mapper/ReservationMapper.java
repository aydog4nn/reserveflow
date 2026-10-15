package com.aydog4nn.reserveflow.inventory.mapper;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.dto.response.ReservationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ReservationMapper {

    @Mapping(target = "productId", source = "product.id")
    ReservationResponse toResponse(Reservation reservation);
}
