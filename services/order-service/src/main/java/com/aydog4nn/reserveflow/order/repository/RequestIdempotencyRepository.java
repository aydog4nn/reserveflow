package com.aydog4nn.reserveflow.order.repository;

import com.aydog4nn.reserveflow.order.domain.RequestIdempotency;
import com.aydog4nn.reserveflow.order.domain.RequestIdempotencyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestIdempotencyRepository extends JpaRepository<RequestIdempotency, RequestIdempotencyId> {
}
