package com.aydog4nn.reserveflow.payment.exception;

public class PaymentTechnicalException extends RuntimeException {

    public PaymentTechnicalException() {
        super("Ödeme sisteminde geçici teknik hata oluştu.");
    }
}
