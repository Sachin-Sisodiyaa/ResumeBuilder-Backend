package com.resumeai.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Returns a graceful JSON fallback when a downstream service is unavailable.
 * Circuit breakers in application.yml forward to these endpoints on open/error.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/{serviceName}")
    public Mono<ResponseEntity<Map<String, Object>>> serviceFallback(@PathVariable("serviceName") String serviceName) {
        return fallback(serviceName, "The requested service is temporarily unavailable. Please try again shortly.");
    }

    @RequestMapping("/generic")
    public Mono<ResponseEntity<Map<String, Object>>> genericFallback() {
        return fallback("service", "This service is temporarily unavailable. Please try again shortly.");
    }

    private Mono<ResponseEntity<Map<String, Object>>> fallback(String service, String message) {
        Map<String, Object> body = Map.of(
                "status",  HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error",   "Service Unavailable",
                "service", service,
                "message", message
        );
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
