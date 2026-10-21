package com.aydog4nn.reserveflow.inventory.messaging.producer;

import com.aydog4nn.reserveflow.inventory.messaging.config.StockFinalizedTopology;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockFinalizedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockFinalizedProducer {

    private final RabbitTemplate rabbitTemplate;

    public StockFinalizedProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(StockFinalizedEvent event) {
        String key = event.outcome() == StockFinalizedEvent.Outcome.COMMITTED
                ? StockFinalizedTopology.COMMITTED_KEY : StockFinalizedTopology.RELEASED_KEY;
        rabbitTemplate.convertAndSend(StockFinalizedTopology.EXCHANGE, key, event);
    }
}
