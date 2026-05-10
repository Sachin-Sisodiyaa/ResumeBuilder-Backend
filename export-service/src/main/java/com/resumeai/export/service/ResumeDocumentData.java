package com.resumeai.export.service;

import java.util.List;

/**
 * Immutable data transfer object carrying all resume content needed
 * by {@link ResumePdfGenerator} and {@link ResumeDocxGenerator}.
 *
 * <p>The export controller or ExportServiceImpl is responsible for
 * assembling this from the resume JSON and section data passed in the
 * export request.
 */
public record ResumeDocumentData(
        String resumeTitle,
        String templateName,
        String templateCategory,
        String fullName,
        String email,
        String phone,
        String location,
        String targetJobTitle,
        String customFont,
        String customColor,
        List<Section> sections
) {
    public ResumeDocumentData(String resumeTitle, String templateName, String templateCategory,
                              String fullName, String email, String phone, String location,
                              String targetJobTitle, List<Section> sections) {
        this(resumeTitle, templateName, templateCategory, fullName, email, phone, location,
                targetJobTitle, null, null, sections);
    }

    /**
     * A single resume section (e.g. Summary, Experience, Education).
     *
     * @param title   section heading shown in bold
     * @param content raw text content for the section
     */
    public record Section(String title, String content) {}

    /** Factory: build from a flat resume title + plain-text content map. */
    public static ResumeDocumentData fromRequest(
            String fullName, String email, String phone,
            String location, String targetJobTitle,
            String resumeTitle, String templateName, String templateCategory,
            String customFont, String customColor, List<Section> sections) {
        return new ResumeDocumentData(
                resumeTitle, templateName, templateCategory,
                fullName, email, phone, location, targetJobTitle,
                customFont, customColor,
                sections == null ? List.of() : sections);
    }

    public static ResumeDocumentData fromRequest(
            String fullName, String email, String phone,
            String location, String targetJobTitle,
            String resumeTitle, String templateName, String templateCategory,
            List<Section> sections) {
        return fromRequest(fullName, email, phone, location, targetJobTitle,
                resumeTitle, templateName, templateCategory, null, null, sections);
    }

    /** Factory: build a minimal document from just name + content string. */
    public static ResumeDocumentData minimal(String fullName, String content) {
        return new ResumeDocumentData(
                fullName, null, null,
                fullName, null, null, null, null, null, null,
                content == null ? List.of() : List.of(new Section("RESUME", content)));
    }
}
