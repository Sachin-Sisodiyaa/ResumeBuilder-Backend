package com.resumeai.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Cloud Gateway — single entry point for all ResumeAI microservices.
 *
 * <p>All client traffic flows through port 8090. The gateway handles:
 * <ul>
 *   <li>Route-based proxying to downstream services</li>
 *   <li>CORS configuration</li>
 *   <li>Request/response logging via a global filter</li>
 *   <li>Circuit-breaker fallback responses</li>
 *   <li>Prometheus metrics at /actuator/prometheus</li>
 * </ul>
 */
@SpringBootApplication
public class GatewayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
