package com.resumeai.web.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebOverview {
    private Map<String, Object> payload;

    public static WebOverview of(Map<String, Object> payload) {
        return new WebOverview(payload);
    }

    public static List<String> services() {
        return List.of("auth-service", "resume-service", "section-service", "ai-service",
            "template-service", "export-service", "jobmatch-service", "notification-service");
    }
}
