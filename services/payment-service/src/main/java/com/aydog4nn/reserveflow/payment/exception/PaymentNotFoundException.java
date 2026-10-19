package com.aydog4nn.reserveflow.payment.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Ödeme bulunamadı: " + paymentId);
    }
}
