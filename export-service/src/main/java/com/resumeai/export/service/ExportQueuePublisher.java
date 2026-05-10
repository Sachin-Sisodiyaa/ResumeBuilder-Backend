package com.resumeai.export.service;

import com.resumeai.export.dto.ExportDtos.ExportQueueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.export", name = "rabbitmq-enabled", havingValue = "true")
public class ExportQueuePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.export.queue-name:resumeai.export.jobs}")
    private String queueName;

    public boolean publish(ExportQueueMessage message) {
        try {
            rabbitTemplate.convertAndSend(queueName, message);
            log.info("Queued export job {} as {}", message.jobId(), message.format());
            return true;
        } catch (AmqpException ex) {
            log.warn("RabbitMQ unavailable for export job {}; falling back to local async: {}",
                    message.jobId(), ex.getMessage());
            return false;
        }
    }
}
