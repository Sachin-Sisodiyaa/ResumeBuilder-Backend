package com.resumeai.template.dto;

public final class TemplateDtos {
    private TemplateDtos() {
    }

    public record TemplateRequest(String name, String description, String thumbnailUrl, String htmlLayout,
                                  String cssStyles, String category, Boolean premium, Boolean active) {
    }
}
