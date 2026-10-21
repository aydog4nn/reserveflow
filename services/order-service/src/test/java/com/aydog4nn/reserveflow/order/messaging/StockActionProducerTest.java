package com.aydog4nn.reserveflow.order.messaging;

import com.aydog4nn.reserveflow.order.messaging.config.StockActionTopology;
import com.aydog4nn.reserveflow.order.messaging.dto.StockActionCommand;
import com.aydog4nn.reserveflow.order.messaging.producer.StockActionProducer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StockActionProducerTest {

    @Test
    void separatesCommitAndReleaseRoutes() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        StockActionProducer producer = new StockActionProducer(template);
        StockActionCommand command = new StockActionCommand(UUID.randomUUID(), UUID.randomUUID());
        producer.commit(command);
        producer.release(command);
        verify(template).convertAndSend("reserveflow.commands", "inventory.commit.v1", command);
        verify(template).convertAndSend("reserveflow.commands", "inventory.release.v1", command);
        StockActionTopology topology = new StockActionTopology();
        assertEquals("inventory.commit.learning", topology.stockCommitBinding().getDestination());
        assertEquals("inventory.release.learning", topology.stockReleaseBinding().getDestination());
    }
}
