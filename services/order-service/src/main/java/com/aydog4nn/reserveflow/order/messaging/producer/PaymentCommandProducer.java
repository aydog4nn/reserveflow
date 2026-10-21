package com.aydog4nn.reserveflow.order.messaging.producer;

import com.aydog4nn.reserveflow.order.messaging.config.RabbitMqConfiguration;
import com.aydog4nn.reserveflow.order.messaging.dto.RequestPaymentCommand;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentCommandProducer {

    private final RabbitTemplate rabbitTemplate;


    public PaymentCommandProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }


    public void sendRequestPayment(RequestPaymentCommand command) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfiguration.COMMANDS_EXCHANGE,
                RabbitMqConfiguration.PAYMENT_ROUTING_KEY,
                command
        );
    }


}
