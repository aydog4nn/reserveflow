package com.aydog4nn.reserveflow.inventory.messaging.consumer;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryCommandConsumer {

    @RabbitListener(queues = RabbitMqConfiguration.RESERVE_STOCK_QUEUE)
    public void receive(ReserveStockCommand command) {
        System.out.println("Stok ayırma mesajı geldi: " + command);
    }


}
