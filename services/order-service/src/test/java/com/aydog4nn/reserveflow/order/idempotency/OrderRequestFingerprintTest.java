package com.aydog4nn.reserveflow.order.idempotency;

import com.aydog4nn.reserveflow.order.domain.PaymentScenario;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotencyId;
import com.aydog4nn.reserveflow.order.dto.request.CreateOrderRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderRequestFingerprintTest {

    private final OrderRequestFingerprint fingerprint = new OrderRequestFingerprint();

    @Test
    void sameBusinessRequestHasSameStableHash() {
        UUID productId = UUID.randomUUID();
        var first = new CreateOrderRequest(productId, 2, PaymentScenario.SUCCESS);
        var second = new CreateOrderRequest(productId, 2, PaymentScenario.SUCCESS);
        assertEquals(fingerprint.hash(first), fingerprint.hash(second));
        assertTrue(fingerprint.hash(first).matches("[0-9a-f]{64}"));
    }

    @Test
    void changingAnyBusinessFieldChangesHash() {
        UUID productId = UUID.randomUUID();
        String original = fingerprint.hash(new CreateOrderRequest(productId, 2, PaymentScenario.SUCCESS));
        assertNotEquals(original, fingerprint.hash(
                new CreateOrderRequest(UUID.randomUUID(), 2, PaymentScenario.SUCCESS)));
        assertNotEquals(original, fingerprint.hash(
                new CreateOrderRequest(productId, 3, PaymentScenario.SUCCESS)));
        assertNotEquals(original, fingerprint.hash(
                new CreateOrderRequest(productId, 2, PaymentScenario.DECLINE)));
    }

    @Test
    void rejectsInvalidRequests() {
        assertThrows(IllegalArgumentException.class, () -> fingerprint.hash(null));
        assertThrows(IllegalArgumentException.class, () -> fingerprint.hash(
                new CreateOrderRequest(null, 1, PaymentScenario.SUCCESS)));
        assertThrows(IllegalArgumentException.class, () -> fingerprint.hash(
                new CreateOrderRequest(UUID.randomUUID(), null, PaymentScenario.SUCCESS)));
        assertThrows(IllegalArgumentException.class, () -> fingerprint.hash(
                new CreateOrderRequest(UUID.randomUUID(), 0, PaymentScenario.SUCCESS)));
        assertThrows(IllegalArgumentException.class, () -> fingerprint.hash(
                new CreateOrderRequest(UUID.randomUUID(), 1, null)));
    }

    @Test
    void compositeIdentityIncludesBothClientAndKey() {
        var first = new RequestIdempotencyId("client-a", "key-1");
        var same = new RequestIdempotencyId("client-a", "key-1");
        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, new RequestIdempotencyId("client-b", "key-1"));
        assertNotEquals(first, new RequestIdempotencyId("client-a", "key-2"));
    }
}
