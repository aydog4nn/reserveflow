package com.aydog4nn.reserveflow.order.messaging.producer;

import com.aydog4nn.reserveflow.order.messaging.config.StockActionTopology;
import com.aydog4nn.reserveflow.order.messaging.dto.StockActionCommand;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockActionProducer {

    private final RabbitTemplate rabbitTemplate;

    public StockActionProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void commit(StockActionCommand command) {
        rabbitTemplate.convertAndSend(StockActionTopology.EXCHANGE, StockActionTopology.COMMIT_KEY, command);
    }

    public void release(StockActionCommand command) {
        rabbitTemplate.convertAndSend(StockActionTopology.EXCHANGE, StockActionTopology.RELEASE_KEY, command);
    }
}
