package com.aydog4nn.reserveflow.inventory.dto.response;


import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal unitPrice,
        String currency,
        boolean active,
        int onHand,
        int reserved,
        int available
) {

}
