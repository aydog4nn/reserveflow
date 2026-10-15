package com.aydog4nn.reserveflow.inventory.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "SKU zorunlu.")
        @Size(max = 64, message = "SKU en fazla 64 karakter olmalı.")
        String sku,
        @NotBlank(message = "Ürün adı zorunlu.")
        @Size(max = 255, message = "Ürün adı en fazla 255 karakter olmalı.")
        String name,
        @NotNull(message = "Birim fiyat zorunlu.")
        @PositiveOrZero(message = "Birim fiyat negatif olamaz.")
        @Digits(integer = 17, fraction = 2, message = "Birim fiyat en fazla 17 tam ve 2 ondalık basamak içermeli.")
        BigDecimal unitPrice,
        @NotBlank(message = "Para birimi zorunlu.")
        @Pattern(regexp = "[A-Z]{3}", message = "Para birimi üç büyük harften oluşmalı.")
        String currency,
        @NotNull(message = "Başlangıç stoğu zorunlu.")
        @PositiveOrZero(message = "Başlangıç stoğu negatif olamaz.")
        Integer initialStock
) {
}
