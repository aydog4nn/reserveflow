package com.aydog4nn.reserveflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "request_idempotency")
public class RequestIdempotency implements Persistable<RequestIdempotencyId> {

    @Valid
    @NotNull
    @EmbeddedId
    private RequestIdempotencyId id;

    @NotNull
    @Pattern(regexp = "[0-9a-f]{64}")
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @NotNull
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean newRecord = true;

    protected RequestIdempotency() {
    }

    public RequestIdempotency(String clientId, String idempotencyKey, String requestHash, UUID orderId) {
        this.id = new RequestIdempotencyId(clientId, idempotencyKey);
        this.requestHash = requestHash;
        this.orderId = orderId;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    protected void markPersisted() {
        this.newRecord = false;
    }

    @Override
    public RequestIdempotencyId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
