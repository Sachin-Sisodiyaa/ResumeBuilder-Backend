package com.resumeai.export;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableRabbit
@EnableScheduling
public class ExportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExportServiceApplication.class, args);
    }
}
