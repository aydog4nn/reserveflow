package com.aydog4nn.reserveflow.order.messaging;

import com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.order.messaging.dto.StockReservedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockReservedEventConversionTest {

    @Test
    void readsInventoryJsonIntoOrderEventWithoutInventoryDependency() {
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","reservationId":"%s"}
                """.formatted(orderId, reservationId);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__",
                "com.aydog4nn.reserveflow.inventory.messaging.dto.StockReservedEvent");
        properties.setInferredArgumentType(StockReservedEvent.class);
        Object converted = new RabbitMqConfiguration().rabbitMessageConverter()
                .fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));
        assertEquals(new StockReservedEvent(orderId, reservationId), converted);
    }
}
