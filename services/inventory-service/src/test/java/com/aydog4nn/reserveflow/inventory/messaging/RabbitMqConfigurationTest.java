package com.aydog4nn.reserveflow.inventory.messaging;

import com.aydog4nn.reserveflow.inventory.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.inventory.messaging.dto.ReserveStockCommand;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqConfigurationTest {

    private final RabbitMqConfiguration configuration = new RabbitMqConfiguration();

    @Test
    void declaresMatchingDurableTopology() {
        var queue = configuration.reserveStockQueue();
        var exchange = configuration.commandsExchange();
        var binding = configuration.reserveStockBinding(queue, exchange);
        assertEquals("inventory.reserve.learning", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("topic", exchange.getType());
        assertEquals("reserveflow.commands", binding.getExchange());
        assertEquals(queue.getName(), binding.getDestination());
        assertEquals("inventory.reserve.v1", binding.getRoutingKey());
    }

    @Test
    void readsOrderJsonIntoInventoryTypeWithoutOrderDependency() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","productId":"%s","quantity":3}
                """.formatted(orderId, productId);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "com.aydog4nn.reserveflow.order.messaging.dto.ReserveStockCommand");
        properties.setInferredArgumentType(ReserveStockCommand.class);
        Object converted = configuration.rabbitMessageConverter().fromMessage(
                new Message(json.getBytes(StandardCharsets.UTF_8), properties));
        assertEquals(new ReserveStockCommand(orderId, productId, 3), converted);
    }
}
