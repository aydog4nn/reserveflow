package com.aydog4nn.reserveflow.order.repository;

import com.aydog4nn.reserveflow.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface
OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT purchase FROM Order purchase WHERE purchase.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);
}
