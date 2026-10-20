package com.aydog4nn.reserveflow.inventory.messaging.config;

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
    public static final String RESERVE_STOCK_QUEUE = "inventory.reserve.learning";
    public static final String RESERVE_STOCK_ROUTING_KEY = "inventory.reserve.v1";

    public static final String EVENTS_EXCHANGE = "reserveflow.events";
    public static final String STOCK_RESERVED_QUEUE = "order.stock-reserved.learning";
    public static final String STOCK_RESERVED_ROUTING_KEY = "inventory.reserved.v1";


    @Bean
    public JacksonJsonMessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue reserveStockQueue() {
        return new Queue(RESERVE_STOCK_QUEUE, true);
    }

    @Bean
    public TopicExchange commandsExchange() {
        return new TopicExchange(COMMANDS_EXCHANGE, true, false);
    }

    @Bean
    public Binding reserveStockBinding(Queue reserveStockQueue, TopicExchange commandsExchange) {
        return BindingBuilder.bind(reserveStockQueue)
                .to(commandsExchange)
                .with(RESERVE_STOCK_ROUTING_KEY);
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue stockReservedQueue() {
        return new Queue(STOCK_RESERVED_QUEUE, true);
    }

    @Bean
    public Binding stockReservedBinding() {
        return BindingBuilder.bind(stockReservedQueue())
                .to(eventsExchange())
                .with(STOCK_RESERVED_ROUTING_KEY);
    }
}
