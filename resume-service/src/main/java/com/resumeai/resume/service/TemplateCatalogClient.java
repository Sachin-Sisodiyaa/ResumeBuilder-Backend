package com.resumeai.resume.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class TemplateCatalogClient {
    private final RestClient restClient;

    public TemplateCatalogClient(@Value("${app.template.base-url:http://localhost:8085}") String templateServiceUrl) {
        this.restClient = RestClient.create(templateServiceUrl);
    }

    public TemplateSummary validateUsableTemplate(Long templateId, String subscriptionPlan) {
        if (templateId == null) {
            return null;
        }

        TemplateSummary template = fetchTemplate(templateId);
        if (template == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected template was not found");
        }
        if (!template.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected template is inactive");
        }
        if (template.premium() && !"PREMIUM".equalsIgnoreCase(subscriptionPlan)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Premium template requires a premium plan");
        }
        return template;
    }

    public void incrementUsage(Long templateId) {
        if (templateId == null) {
            return;
        }

        try {
            restClient.put()
                    .uri("/api/v1/templates/{templateId}/usage", templateId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.debug("Template usage update skipped for template {}: {}", templateId, ex.getMessage());
        }
    }

    private TemplateSummary fetchTemplate(Long templateId) {
        try {
            return restClient.get()
                    .uri("/api/v1/templates/{templateId}", templateId)
                    .retrieve()
                    .body(TemplateSummary.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected template was not found", ex);
        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected template cannot be used", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Template service is not available right now. Create without a template or try again shortly.", ex);
        }
    }

    public record TemplateSummary(Long templateId, String name, boolean premium, boolean active) {
    }
}
