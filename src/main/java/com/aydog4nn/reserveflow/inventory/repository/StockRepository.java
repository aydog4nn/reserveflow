package com.aydog4nn.reserveflow.inventory.repository;

import com.aydog4nn.reserveflow.inventory.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT stock FROM Stock stock WHERE stock.productId = :productId")
    Optional<Stock> findByProductIdForUpdate(
            @Param("productId") UUID productId);


}
