package com.aydog4nn.reserveflow.payment.repository;

import com.aydog4nn.reserveflow.payment.domain.Payment;
import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findTop100ByStatusAndScenarioAndReadyAtLessThanEqualOrderByReadyAtAsc(
            PaymentStatus status,
            PaymentScenario scenario,
            Instant readyAt
    );
}
