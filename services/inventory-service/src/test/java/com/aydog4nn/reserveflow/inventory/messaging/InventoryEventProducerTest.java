package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.dto.StockReservedEvent;
import com.aydog4nn.reserveflow.inventory.messaging.producer.InventoryEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryEventProducerTest {

    @Test
    void publishesReservationResultWithCorrectRouting() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        StockReservedEvent event = new StockReservedEvent(UUID.randomUUID(), UUID.randomUUID());
        new InventoryEventProducer(template).sendStockReserved(event);
        verify(template).convertAndSend("reserveflow.events", "inventory.reserved.v1", event);
    }

    @Test
    void resultTopologyRoutesToOrderQueue() {
        RabbitMqConfiguration config = new RabbitMqConfiguration();
        assertTrue(config.stockReservedQueue().isDurable());
        assertEquals("topic", config.eventsExchange().getType());
        var binding = config.stockReservedBinding();
        assertEquals("reserveflow.events", binding.getExchange());
        assertEquals("order.stock-reserved.learning", binding.getDestination());
        assertEquals("inventory.reserved.v1", binding.getRoutingKey());
    }
}
