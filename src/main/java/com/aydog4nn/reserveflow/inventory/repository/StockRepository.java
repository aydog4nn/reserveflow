package com.aydog4nn.reserveflow.inventory.repository;

import com.aydog4nn.reserveflow.inventory.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {
}
