package com.aydog4nn.reserveflow.order.messaging.dto;

import java.util.UUID;

public record ReserveStockCommand(
        UUID orderId,
        UUID productId,
        int quantity

) { }
