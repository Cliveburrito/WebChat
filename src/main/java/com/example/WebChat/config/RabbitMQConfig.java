package com.example.WebChat.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for RabbitMQ.
 * This setup enables asynchronous message processing for the chat system,
 * allowing the API to remain responsive while messages are processed in the background.
 */
@Configuration
public class RabbitMQConfig {

    // The name of the exchange that acts as a message router
    public static final String CHAT_EXCHANGE = "chat.exchange";

    // The name of the queue where chat messages will be stored before consumption
    public static final String CHAT_QUEUE = "chat.messages.queue";
    public static final String WATERMARK_QUEUE = "chat.watermark.queue";

    // The specific key used to route messages from the exchange to this specific queue
    public static final String CHAT_ROUTING_KEY = "chat.message.routingKey";
    public static final String WATERMARK_ROUTING_KEY = "chat.watermark.routingKey";

    public static final String FILE_LINK_QUEUE = "chat.files.link.queue";
    public static final String FILE_LINK_ROUTING_KEY = "chat.files.link.routingKey";

    /**
     * Defines a durable queue.
     * Setting 'durable' to true ensures the queue (and its messages)
     * survives a RabbitMQ broker restart.
     */
    @Bean
    public Queue chatQueue() {
        return new Queue(CHAT_QUEUE, true);
    }

    /**
     * Defines a Topic Exchange.
     * Topic exchanges route messages to queues based on wildcard matches
     * between the routing key and the binding pattern.
     */
    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(CHAT_EXCHANGE);
    }

    /**
     * Creates a Binding between the Chat Queue and the Chat Exchange.
     * This "glues" the infrastructure together: whenever a message is sent to the
     * exchange with the specific Routing Key, it is delivered to the chatQueue.
     */
    @Bean
    public Binding binding(Queue chatQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(chatQueue).to(chatExchange).with(CHAT_ROUTING_KEY);
    }

    @Bean
    public Queue fileLinkQueue() {
        return new Queue(FILE_LINK_QUEUE, true);
    }


    @Bean
    public Binding fileBinding(Queue fileLinkQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(fileLinkQueue).to(chatExchange).with(FILE_LINK_ROUTING_KEY);
    }

    @Bean
    public Queue watermarkQueue() {
        return new Queue(WATERMARK_QUEUE, true);
    }

    @Bean
    public Binding watermarkBinding(Queue watermarkQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(watermarkQueue).to(chatExchange).with(WATERMARK_ROUTING_KEY);
    }

    /**
     * Configures a JSON Message Converter.
     * By default, RabbitMQ sends messages as byte arrays (binary).
     * This Bean tells Spring to automatically serialize Java objects to JSON
     * when sending, and deserialize JSON back to Java objects when receiving.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        org.springframework.amqp.rabbit.core.RabbitTemplate template = new org.springframework.amqp.rabbit.core.RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}