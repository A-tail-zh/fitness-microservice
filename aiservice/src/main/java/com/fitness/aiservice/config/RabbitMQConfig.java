package com.fitness.aiservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.queue.name}")
    private String activityQueueName;

    @Value("${rabbitmq.exchange.name}")
    private String activityExchangeName;

    @Value("${rabbitmq.routing.key}")
    private String activityRoutingKey;

    @Value("${notification.queue.email}")
    private String emailQueueName;

    @Value("${notification.exchange.name}")
    private String notificationExchangeName;

    @Value("${notification.routing-key.email}")
    private String emailRoutingKey;

    @Bean
    public Queue activityQueue() {
        return new Queue(activityQueueName, true);
    }

    @Bean
    public DirectExchange activityExchange() {
        return new DirectExchange(activityExchangeName);
    }

    @Bean
    public Binding activityBinding(Queue activityQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityQueue).to(activityExchange).with(activityRoutingKey);
    }

    @Bean
    public Queue emailNotificationQueue() {
        return new Queue(emailQueueName, true);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(notificationExchangeName);
    }

    @Bean
    public Binding emailNotificationBinding(Queue emailNotificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(emailNotificationQueue).to(notificationExchange).with(emailRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
