package com.resumeai.export.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.resumeai.export.model.ExportJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class ResumePdfGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesStructuredTemplatePdf() throws Exception {
        ResumeHtmlTemplateResolver resolver = new ResumeHtmlTemplateResolver();
        ResumePdfGenerator generator = new ResumePdfGenerator(resolver);
        ReflectionTestUtils.setField(generator, "storageDir", tempDir.toString());

        ExportJob job = new ExportJob();
        job.setJobId("structured-job");

        ResumeDocumentData data = new ResumeDocumentData(
                "Product Manager Pro Resume",
                "Product Manager Pro",
                "PROFESSIONAL",
                "Sachin Sisodiya",
                "sachin@example.com",
                "9131185746",
                null,
                "Target role",
                List.of(
                        new ResumeDocumentData.Section("Professional Summary", "Results-driven Target role with experience improving workflows."),
                        new ResumeDocumentData.Section("Core Skills", "Leadership, communication, problem solving, analytics"),
                        new ResumeDocumentData.Section("Professional Experience", "- Delivered high-impact work aligned with team goals.")
                ));

        Path file = generator.generate(job, data);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void templateUsesPrintWidthAndStructuredContentBlocks() {
        ResumeHtmlTemplateResolver resolver = new ResumeHtmlTemplateResolver();

        ResumeDocumentData data = new ResumeDocumentData(
                "Software Resume",
                "Software Engineer",
                "TECHNOLOGY",
                "Sachin Sisodiya",
                "sachin@example.com",
                null,
                null,
                "Software Engineer",
                List.of(
                        new ResumeDocumentData.Section("Skills", "Programming Languages: Java\nPROJECT AND EXPERIENCE\n- Built APIs\n* Improved exports"),
                        new ResumeDocumentData.Section("Summary", "Full stack engineer")
                ));

        String xhtml = resolver.resolve(data);

        assertTrue(xhtml.contains("@page { size: A4; margin: 12mm; }"));
        assertTrue(xhtml.contains(".resume { width: 100%;"));
        assertFalse(xhtml.contains("width: 595px"));
        assertTrue(xhtml.contains("<ul><li>Built APIs</li><li>Improved exports</li></ul>"));
        assertTrue(xhtml.contains("<p class=\"content-heading\">PROJECT AND EXPERIENCE</p>"));
    }
}
