package com.aydog4nn.reserveflow.inventory.repository;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
}
