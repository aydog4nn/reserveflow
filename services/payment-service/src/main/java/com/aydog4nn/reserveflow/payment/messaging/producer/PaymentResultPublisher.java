package com.aydog4nn.reserveflow.payment.messaging.producer;

import com.aydog4nn.reserveflow.payment.messaging.config.PaymentResultTopology;
import com.aydog4nn.reserveflow.payment.messaging.dto.PaymentResultEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PaymentResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentResultEvent event) {
        String routingKey = event.outcome() == PaymentResultEvent.Outcome.SUCCEEDED
                ? PaymentResultTopology.SUCCEEDED_KEY : PaymentResultTopology.FAILED_KEY;
        rabbitTemplate.convertAndSend(PaymentResultTopology.EXCHANGE, routingKey, event);
    }
}
