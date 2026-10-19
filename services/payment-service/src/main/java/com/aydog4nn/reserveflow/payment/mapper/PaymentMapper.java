package com.aydog4nn.reserveflow.payment.mapper;

import com.aydog4nn.reserveflow.payment.domain.Payment;
import com.aydog4nn.reserveflow.payment.dto.response.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface PaymentMapper {

    @Mapping(target = "paymentId", source = "id")
    PaymentResponse toResponse(Payment payment);
}
