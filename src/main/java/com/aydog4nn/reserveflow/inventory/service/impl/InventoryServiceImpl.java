package com.aydog4nn.reserveflow.inventory.service.impl;

import com.aydog4nn.reserveflow.inventory.repository.ProductRepository;
import com.aydog4nn.reserveflow.inventory.repository.ReservationRepository;
import com.aydog4nn.reserveflow.inventory.repository.StockRepository;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import org.springframework.stereotype.Service;

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
}
