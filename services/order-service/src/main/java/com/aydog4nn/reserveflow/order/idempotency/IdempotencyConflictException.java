package com.aydog4nn.reserveflow.order.idempotency;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Aynı anahtar farklı bir sipariş isteği için kullanılamaz.");
    }
}
