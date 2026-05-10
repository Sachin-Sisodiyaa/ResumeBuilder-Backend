package com.resumeai.ai;

import com.resumeai.ai.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        DotenvLoader.loadFromWorkingTree();
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
