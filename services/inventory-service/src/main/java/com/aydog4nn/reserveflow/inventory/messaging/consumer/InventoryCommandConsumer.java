package com.aydog4nn.reserveflow.inventory.messaging.consumer;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandConsumer {


    private final IInventoryService inventoryService;

    public InventoryCommandConsumer(IInventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @RabbitListener(queues = RabbitMqConfiguration.RESERVE_STOCK_QUEUE)
    public void receive(ReserveStockCommand command) {
        inventoryService.reserveStock(
                command.orderId(),
                command.productId(),
                command.quantity()
        );

        System.out.println("Stok rezervasyonu tamamlandı: " + command);
    }


}
