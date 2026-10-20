package com.aydog4nn.reserveflow.inventory.messaging.producer;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockReservedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryEventProducer {


    private final RabbitTemplate rabbitTemplate;

    public InventoryEventProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;


    }

    public void sendStockReserved(StockReservedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfiguration.EVENTS_EXCHANGE,
                RabbitMqConfiguration.STOCK_RESERVED_ROUTING_KEY,
                event
        );
    }
}
