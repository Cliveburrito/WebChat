package com.example.WebChat.UtilsConfigs;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_QUEUE = "chat.messages.queue";
    public static final String CHAT_ROUTING_KEY = "chat.message.routingKey";

    @Bean
    public Queue chatQueue() {
        return new Queue(CHAT_QUEUE, true); // durable true means it wont get lost if rmq falls
    }

    @Bean
    public TopicExchange chatExchange() {
        return new TopicExchange(CHAT_EXCHANGE);
    }

    // 3. Συνδέουμε την Ουρά με τον Exchange μέσω ενός Routing Key
    @Bean
    public Binding binding(Queue chatQueue, TopicExchange chatExchange) {
        return BindingBuilder.bind(chatQueue).to(chatExchange).with(CHAT_ROUTING_KEY);
    }

    // 4. ΠΟΛΥ ΣΗΜΑΝΤΙΚΟ: Μετατροπέας σε JSON
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
