package com.aydog4nn.reserveflow.inventory.service.impl;

import com.aydog4nn.reserveflow.inventory.domain.Product;
import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.domain.Stock;
import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.ReservationRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryServiceImpl implements IInventoryService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    public InventoryServiceImpl(ProductRepository productRepository, StockRepository stockRepository, ReservationRepository reservationRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    private void validateReserve(UUID orderId, UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Rezervasyon miktarı pozitif olmalı."
            );
        }

        if (orderId == null || productId == null) {
            throw new IllegalArgumentException(
                    "Sipariş ve ürün kimliği zorunlu."
            );
        }
    }

    private Product findActiveProduct(UUID productId){

        Optional<Product> productById = productRepository.findById(productId);

        if (productById.isEmpty()){
            throw new IllegalArgumentException("Urun bulunamadi!");
        }

        if ( !productById.get().isActive()){
            throw new IllegalArgumentException("Urun aktif degil!");
        }

        return productById.get();


    }

    private Optional<Reservation> validateExistingReservation(
            UUID orderId, UUID productId, int quantity) {

        Optional<Reservation> existingReservation =
                reservationRepository.findByOrderId(orderId);

        if (existingReservation.isEmpty()) {
            return Optional.empty();
        }

        Reservation reservation = existingReservation.get();

        boolean sameProduct = reservation.getProduct().getId().equals(productId);
        boolean sameQuantity = reservation.getQuantity() == quantity;

        if (!sameProduct || !sameQuantity) {
            throw new IllegalArgumentException(
                    "Bu sipariş için farklı ürün veya miktarla rezervasyon mevcut."
            );
        }

        return existingReservation;
    }

    private Stock findAndValidateStock(UUID productId, int quantity) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Ürünün stok kaydı bulunamadı.")
                );

        if (quantity > stock.getAvailable()) {
            throw new IllegalArgumentException("Yetersiz stok.");
        }

        return stock;
    }

    @Override
    @Transactional
    public Reservation reserveStock(UUID orderId, UUID productId, int quantity) {
        validateReserve(orderId, productId, quantity);

        Optional<Reservation> existingReservation =
                validateExistingReservation(orderId, productId, quantity);

        if (existingReservation.isPresent()) {
            return existingReservation.get();
        }

        Product product = findActiveProduct(productId);
        Stock stock = findAndValidateStock(productId, quantity);

        stock.setReserved(stock.getReserved() + quantity);

        Reservation reservation = new Reservation(orderId, product, quantity);
        return reservationRepository.save(reservation);
    }
}
