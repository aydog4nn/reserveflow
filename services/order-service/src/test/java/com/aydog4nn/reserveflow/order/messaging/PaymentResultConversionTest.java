package com.aydog4nn.reserveflow.order.messaging;

import com.aydog4nn.reserveflow.order.messaging.config.PaymentResultTopology;
import com.aydog4nn.reserveflow.order.messaging.dto.PaymentResultEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentResultConversionTest {

    @ParameterizedTest
    @EnumSource(PaymentResultEvent.Outcome.class)
    void convertsPaymentJsonToLocalOrderType(PaymentResultEvent.Outcome outcome) {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String reason = outcome == PaymentResultEvent.Outcome.FAILED ? "Declined" : null;
        String json = """
                {"orderId":"%s","paymentId":"%s","amount":1500.00,"currency":"TRY",
                 "outcome":"%s","failureReason":%s}
                """.formatted(orderId, paymentId, outcome, reason == null ? "null" : "\"" + reason + "\"");
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("__TypeId__", "com.aydog4nn.reserveflow.payment.messaging.dto.PaymentResultEvent");
        properties.setInferredArgumentType(PaymentResultEvent.class);
        Object result = new com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration()
                .rabbitMessageConverter().fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8), properties));
        assertEquals(new PaymentResultEvent(orderId, paymentId, new BigDecimal("1500.00"),
                "TRY", outcome, reason), result);
        PaymentResultTopology topology = new PaymentResultTopology();
        assertEquals("order.payment-results.learning", topology.paymentResultQueue().getName());
        assertEquals(outcome == PaymentResultEvent.Outcome.SUCCEEDED ? "payment.succeeded.v1" : "payment.failed.v1",
                outcome == PaymentResultEvent.Outcome.SUCCEEDED
                        ? topology.paymentSucceededBinding().getRoutingKey() : topology.paymentFailedBinding().getRoutingKey());
    }
}
