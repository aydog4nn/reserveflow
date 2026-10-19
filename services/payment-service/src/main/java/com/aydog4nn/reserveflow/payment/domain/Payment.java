package com.aydog4nn.reserveflow.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "order_id", nullable = false, updatable = false, unique = true)
    private UUID orderId;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private PaymentScenario scenario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Size(max = 500)
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(UUID orderId, BigDecimal amount, String currency, PaymentScenario scenario) {
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.scenario = scenario;
        this.status = PaymentStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentScenario getScenario() {
        return scenario;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public void setReadyAt(Instant readyAt) {
        this.readyAt = readyAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
