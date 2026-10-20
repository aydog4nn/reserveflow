package com.aydog4nn.reserveflow.inventory.messaging.consumer;

import com.aydog4nn.reserveflow.inventory.domain.Reservation;
import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.inventory.messaging.producer.InventoryEventProducer;
import com.aydog4nn.reserveflow.inventory.service.abs.IInventoryService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandConsumer {


    private final IInventoryService inventoryService;
    private final InventoryEventProducer eventProducer;

    public InventoryCommandConsumer(IInventoryService inventoryService, InventoryEventProducer eventProducer) {
        this.inventoryService = inventoryService;
        this.eventProducer = eventProducer;
    }

    @RabbitListener(queues = RabbitMqConfiguration.RESERVE_STOCK_QUEUE)
    public void receive(ReserveStockCommand command) {
        Reservation reservation = inventoryService.reserveStock(
                command.orderId(),
                command.productId(),
                command.quantity()
        );

        StockReservedEvent event = new StockReservedEvent(
                reservation.getOrderId(),
                reservation.getId()
        );

        eventProducer.sendStockReserved(event);
    }




}
