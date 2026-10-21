package com.aydog4nn.reserveflow.payment.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentResultTopology {

    public static final String EXCHANGE = "reserveflow.events";
    public static final String QUEUE = "order.payment-results.learning";
    public static final String SUCCEEDED_KEY = "payment.succeeded.v1";
    public static final String FAILED_KEY = "payment.failed.v1";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentResultQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentResultQueue()).to(paymentEventsExchange()).with(SUCCEEDED_KEY);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder.bind(paymentResultQueue()).to(paymentEventsExchange()).with(FAILED_KEY);
    }
}
