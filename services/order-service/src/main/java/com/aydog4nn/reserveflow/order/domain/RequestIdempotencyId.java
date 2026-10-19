package com.aydog4nn.reserveflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RequestIdempotencyId implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @Size(max = 64)
    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @NotBlank
    @Size(max = 128)
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected RequestIdempotencyId() {
    }

    public RequestIdempotencyId(String clientId, String idempotencyKey) {
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getClientId() {
        return clientId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RequestIdempotencyId that)) {
            return false;
        }
        return Objects.equals(clientId, that.clientId)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, idempotencyKey);
    }
}
