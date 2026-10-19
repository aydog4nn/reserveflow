package com.aydog4nn.reserveflow.order.client;

import com.aydog4nn.reserveflow.order.client.dto.InventoryProductResponse;

import java.util.UUID;

public interface InventoryClient {

    InventoryProductResponse getProduct(UUID productId);
}
