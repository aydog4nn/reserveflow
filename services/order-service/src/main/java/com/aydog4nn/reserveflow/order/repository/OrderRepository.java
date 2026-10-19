package com.aydog4nn.reserveflow.order.repository;

import com.aydog4nn.reserveflow.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface
OrderRepository extends JpaRepository<Order, UUID> {
}
