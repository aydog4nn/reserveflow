package com.aydog4nn.reserveflow.order.messaging.producer;


import com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.order.messaging.dto.ReserveStockCommand;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryCommandProducer {

    private final RabbitTemplate rabbitTemplate;

    public InventoryCommandProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }


    public void sendReserveStock(ReserveStockCommand command) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfiguration.COMMANDS_EXCHANGE,
                RabbitMqConfiguration.RESERVE_STOCK_ROUTING_KEY,
                command
        );
    }

}
