package com.jperez.lgsstorecrm.common.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "store.events";
    public static final String ROUTING_KEY_CREDIT_TRANSACTION_APPLIED = "credit-transaction.applied";

    @Bean
    public TopicExchange storeEventsExchange() {
        return new TopicExchange(EXCHANGE);
    }
}
