package com.aydog4nn.reserveflow.order.idempotency;

import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OrderRequestFingerprint {

    public String hash(CreateOrderRequest request) {
        if (request == null || request.productId() == null || request.quantity() == null
                || request.quantity() <= 0 || request.paymentScenario() == null) {
            throw new IllegalArgumentException("İstek özeti için geçerli sipariş bilgileri zorunlu.");
        }
        String canonical = "v1|" + request.productId() + "|"
                + request.quantity() + "|" + request.paymentScenario().name();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algoritması kullanılamıyor.", exception);
        }
    }
}
