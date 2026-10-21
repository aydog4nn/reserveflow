package com.aydog4nn.reserveflow.payment.messaging;

import com.aydog4nn.reserveflow.payment.domain.PaymentScenario;
import com.aydog4nn.reserveflow.payment.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.payment.messaging.dto.RequestPaymentCommand;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqConfigurationTest {

    private final RabbitMqConfiguration config = new RabbitMqConfiguration();

    @Test
    void declaresDurablePaymentRoute() {
        var queue = config.paymentQueue();
        var exchange = config.commandsExchange();
        var binding = config.paymentBinding(queue, exchange);
        assertTrue(queue.isDurable());
        assertEquals("topic", exchange.getType());
        assertEquals("reserveflow.commands", binding.getExchange());
        assertEquals("payment.request.learning", binding.getDestination());
        assertEquals("payment.request.v1", binding.getRoutingKey());
    }

    @Test
    void readsOrderJsonIntoPaymentModelWithoutOrderDependency() {
        UUID orderId = UUID.randomUUID();
        String json = """
                {"orderId":"%s","amount":1500.00,"currency":"TRY","scenario":"SUCCESS"}
                """.formatted(orderId);
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__",
                "com.aydog4nn.reserveflow.order.messaging.dto.RequestPaymentCommand");
        properties.setInferredArgumentType(RequestPaymentCommand.class);
        Object converted = config.rabbitMessageConverter().fromMessage(
                new Message(json.getBytes(StandardCharsets.UTF_8), properties));
        assertEquals(new RequestPaymentCommand(orderId, new BigDecimal("1500.00"),
                "TRY", PaymentScenario.SUCCESS), converted);
    }
}
