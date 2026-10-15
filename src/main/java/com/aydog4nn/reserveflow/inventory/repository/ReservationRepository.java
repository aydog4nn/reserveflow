package com.aydog4nn.reserveflow.inventory.repository;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT reservation FROM Reservation reservation
        WHERE reservation.orderId = :orderId
        """)
    Optional<Reservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

}
