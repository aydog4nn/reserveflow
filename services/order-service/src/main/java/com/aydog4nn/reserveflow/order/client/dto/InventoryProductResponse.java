package com.aydog4nn.reserveflow.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryProductResponse(
        @NotNull UUID id,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Boolean active
) {
}
