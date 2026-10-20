package com.aydog4nn.reserveflow.order.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfiguration {

    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    public static final String COMMANDS_EXCHANGE = "reserveflow.commands";
    public static final String RESERVE_STOCK_QUEUE = "inventory.reserve.learning";
    public static final String RESERVE_STOCK_ROUTING_KEY = "inventory.reserve.v1";
    public static final String STOCK_RESERVED_QUEUE = "order.stock-reserved.learning";

    @Bean
    public Queue reserveStockQueue() {
        return new Queue(RESERVE_STOCK_QUEUE, true);
    }


    @Bean
    public TopicExchange commandsExchange() {
        return new TopicExchange(COMMANDS_EXCHANGE, true, false);
    }


    @Bean
    public Binding reserveStockBinding(
            Queue reserveStockQueue,
            TopicExchange commandsExchange) {

        return BindingBuilder.bind(reserveStockQueue)
                .to(commandsExchange)
                .with(RESERVE_STOCK_ROUTING_KEY);
    }

}
