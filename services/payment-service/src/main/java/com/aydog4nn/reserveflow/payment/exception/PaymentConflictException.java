package com.aydog4nn.reserveflow.payment.exception;

public class PaymentConflictException extends IllegalArgumentException {

    public PaymentConflictException() {
        super("Bu sipariş için farklı tutar, para birimi veya senaryoyla ödeme mevcut.");
    }
}
