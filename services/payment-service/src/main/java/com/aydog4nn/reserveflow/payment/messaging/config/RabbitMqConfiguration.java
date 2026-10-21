package com.aydog4nn.reserveflow.payment.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    public static final String COMMANDS_EXCHANGE = "reserveflow.commands";
    public static final String PAYMENT_QUEUE = "payment.request.learning";
    public static final String PAYMENT_ROUTING_KEY = "payment.request.v1";

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue paymentQueue() {
        return new Queue(PAYMENT_QUEUE, true);
    }

    @Bean
    public TopicExchange commandsExchange() {
        return new TopicExchange(COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(paymentQueue).to(commandsExchange).with(PAYMENT_ROUTING_KEY);
    }
}
