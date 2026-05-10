package com.resumeai.export.service;

import com.resumeai.export.model.ExportJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates professional resume PDFs using Flying Saucer (HTML-to-PDF).
 *
 * <p>The generated PDF is rendered from the same HTML/CSS templates used by the
 * frontend builder preview, ensuring visual consistency between what the user
 * sees in the editor and the exported file.
 *
 * <p>Template resolution is delegated to {@link ResumeHtmlTemplateResolver}
 * which builds XHTML with embedded CSS matching each template's layout
 * (sidebar, structured, single-column) and colour palette.
 *
 * <p>Input data is passed via {@link ResumeDocumentData} which the export
 * service assembles from the resume and section content.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResumePdfGenerator {

    private final ResumeHtmlTemplateResolver templateResolver;

    @Value("${app.export.storage-dir:./exports}")
    private String storageDir;

    /**
     * Generates a PDF resume and writes it to the local filesystem.
     *
     * @param job  the export job (provides jobId for filename)
     * @param data resume content and template metadata
     * @return absolute path of the written PDF file
     */
    public Path generate(ExportJob job, ResumeDocumentData data) throws IOException {
        Path dir = Paths.get(storageDir, job.getJobId());
        Files.createDirectories(dir);
        Path filePath = dir.resolve("resume.pdf");

        String xhtml = templateResolver.resolve(data);
        log.debug("Rendering PDF for job {} with template '{}' / '{}'",
                job.getJobId(), data.templateName(), data.templateCategory());

        try (OutputStream os = Files.newOutputStream(filePath)) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(os);
        } catch (Exception e) {
            log.error("Flying Saucer PDF rendering failed for job {}, falling back to basic PDF: {}",
                    job.getJobId(), e.getMessage());
            generateFallbackPdf(filePath, data);
        }

        log.info("PDF generated: {}", filePath);
        return filePath;
    }

    /**
     * Minimal fallback PDF using PDFBox if Flying Saucer rendering fails.
     * This ensures the user always gets a downloadable file.
     */
    private void generateFallbackPdf(Path filePath, ResumeDocumentData data) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            var page = new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
            doc.addPage(page);

            var fontBold = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD);
            var fontRegular = new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);

            float pageHeight = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getHeight();
            float margin = 50f;
            float y = pageHeight - margin;

            try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                // Name
                cs.beginText();
                cs.setFont(fontBold, 18f);
                cs.newLineAtOffset(margin, y - 18f);
                cs.showText(sanitise(data.fullName()));
                cs.endText();
                y -= 28;

                // Contact line
                StringBuilder contact = new StringBuilder();
                if (data.email() != null) contact.append(data.email());
                if (data.phone() != null) {
                    if (!contact.isEmpty()) contact.append("  |  ");
                    contact.append(data.phone());
                }
                cs.beginText();
                cs.setFont(fontRegular, 9f);
                cs.newLineAtOffset(margin, y - 9f);
                cs.showText(sanitise(contact.toString()));
                cs.endText();
                y -= 20;

                // Sections
                for (ResumeDocumentData.Section section : data.sections()) {
                    if (y < margin + 40) break;

                    cs.beginText();
                    cs.setFont(fontBold, 11f);
                    cs.newLineAtOffset(margin, y - 11f);
                    cs.showText(sanitise(section.title().toUpperCase()));
                    cs.endText();
                    y -= 16;

                    for (String line : section.content().split("\n")) {
                        if (y < margin + 20) break;
                        cs.beginText();
                        cs.setFont(fontRegular, 10f);
                        cs.newLineAtOffset(margin, y - 10f);
                        cs.showText(sanitise(line));
                        cs.endText();
                        y -= 14;
                    }
                    y -= 8;
                }
            }
            doc.save(filePath.toFile());
        }
    }

    /** Strips non-Latin1 characters to avoid PDType1Font encoding errors. */
    private String sanitise(String s) {
        if (s == null) return "";
        return s.chars()
                .filter(c -> c >= 32 && c <= 126)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
