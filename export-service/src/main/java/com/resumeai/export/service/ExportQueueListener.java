package com.resumeai.export.service;

import com.resumeai.export.dto.ExportDtos.ExportQueueMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.export", name = "rabbitmq-enabled", havingValue = "true")
public class ExportQueueListener {

    private final ExportServiceImpl exportService;

    @RabbitListener(queues = "${app.export.queue-name:resumeai.export.jobs}")
    public void handle(ExportQueueMessage message) {
        log.info("Processing queued export job {} as {}", message.jobId(), message.format());
        exportService.processQueuedExport(message);
    }
}
