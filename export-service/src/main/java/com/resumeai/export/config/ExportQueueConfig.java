package com.resumeai.export.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.export", name = "rabbitmq-enabled", havingValue = "true")
public class ExportQueueConfig {

    @Bean
    Queue exportQueue(@Value("${app.export.queue-name:resumeai.export.jobs}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    MessageConverter exportMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
