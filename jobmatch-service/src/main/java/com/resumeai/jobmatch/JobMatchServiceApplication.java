package com.resumeai.jobmatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobMatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobMatchServiceApplication.class, args);
    }
}
