package com.aydog4nn.reserveflow.inventory.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StockActionTopology {

    public static final String EXCHANGE = "reserveflow.commands";
    public static final String COMMIT_QUEUE = "inventory.commit.learning";
    public static final String RELEASE_QUEUE = "inventory.release.learning";
    public static final String COMMIT_KEY = "inventory.commit.v1";
    public static final String RELEASE_KEY = "inventory.release.v1";

    @Bean
    public TopicExchange stockActionExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue stockCommitQueue() {
        return new Queue(COMMIT_QUEUE, true);
    }

    @Bean
    public Queue stockReleaseQueue() {
        return new Queue(RELEASE_QUEUE, true);
    }

    @Bean
    public Binding stockCommitBinding() {
        return BindingBuilder.bind(stockCommitQueue()).to(stockActionExchange()).with(COMMIT_KEY);
    }

    @Bean
    public Binding stockReleaseBinding() {
        return BindingBuilder.bind(stockReleaseQueue()).to(stockActionExchange()).with(RELEASE_KEY);
    }
}
