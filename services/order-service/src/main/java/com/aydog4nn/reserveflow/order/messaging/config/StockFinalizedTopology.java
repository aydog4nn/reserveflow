package com.aydog4nn.reserveflow.order.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StockFinalizedTopology {

    public static final String EXCHANGE = "reserveflow.events";
    public static final String QUEUE = "order.stock-finalized.learning";
    public static final String COMMITTED_KEY = "inventory.committed.v1";
    public static final String RELEASED_KEY = "inventory.released.v1";

    @Bean
    public TopicExchange stockFinalizedExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue stockFinalizedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding stockCommittedBinding() {
        return BindingBuilder.bind(stockFinalizedQueue()).to(stockFinalizedExchange()).with(COMMITTED_KEY);
    }

    @Bean
    public Binding stockReleasedBinding() {
        return BindingBuilder.bind(stockFinalizedQueue()).to(stockFinalizedExchange()).with(RELEASED_KEY);
    }
}
