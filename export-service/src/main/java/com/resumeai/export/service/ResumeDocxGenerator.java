package com.resumeai.export.service;

import com.resumeai.export.model.ExportJob;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates ATS-friendly DOCX resumes using Apache POI OOXML 5.x.
 *
 * <p>Layout:
 * <ul>
 *   <li>Name in 20pt bold at top</li>
 *   <li>Contact line in 10pt italic</li>
 *   <li>Each section has a bold heading + thin bottom border + body text</li>
 * </ul>
 */
@Service
@Slf4j
public class ResumeDocxGenerator {
    private static final String DEFAULT_FONT = "Calibri";

    @Value("${app.export.storage-dir:./exports}")
    private String storageDir;

    /**
     * Generates a DOCX resume and writes it to the local filesystem.
     *
     * @param job  the export job (provides jobId for filename)
     * @param data resume content
     * @return absolute path of the written DOCX file
     */
    public Path generate(ExportJob job, ResumeDocumentData data) throws IOException {
        Path dir = Paths.get(storageDir, job.getJobId());
        Files.createDirectories(dir);
        Path filePath = dir.resolve("resume.docx");

        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(filePath.toFile())) {
            TemplateProfile profile = profileFor(data);
            String fontFamily = docxFont(data.customFont());

            // ── Page margins ─────────────────────────────────────────────────
            CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
            CTPageMar pageMar = sectPr.addNewPgMar();
            pageMar.setTop(BigInteger.valueOf(720));    // 0.5"
            pageMar.setBottom(BigInteger.valueOf(720));
            pageMar.setLeft(BigInteger.valueOf(900));   // 0.625"
            pageMar.setRight(BigInteger.valueOf(900));

            // ── Name ─────────────────────────────────────────────────────────
            XWPFParagraph namePara = doc.createParagraph();
            namePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun nameRun = namePara.createRun();
            nameRun.setText(data.fullName() != null ? data.fullName() : "");
            nameRun.setBold(true);
            nameRun.setFontSize(20);
            nameRun.setFontFamily(fontFamily);
            nameRun.setColor(profile.accentHex());

            // ── Contact line ──────────────────────────────────────────────────
            String contact = buildContactLine(data);
            if (!contact.isBlank()) {
                XWPFParagraph contactPara = doc.createParagraph();
                contactPara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun contactRun = contactPara.createRun();
                contactRun.setText(contact);
                contactRun.setItalic(true);
                contactRun.setFontSize(9);
                contactRun.setFontFamily(fontFamily);
            }

            // ── Target job title ──────────────────────────────────────────────
            if (data.targetJobTitle() != null && !data.targetJobTitle().isBlank()) {
                XWPFParagraph titlePara = doc.createParagraph();
                titlePara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun titleRun = titlePara.createRun();
                titleRun.setText(data.targetJobTitle());
                titleRun.setBold(false);
                titleRun.setFontSize(11);
                titleRun.setFontFamily(fontFamily);
                titleRun.setColor(profile.accentHex());
                titlePara.setSpacingAfter(120);
            }

            // ── Sections ──────────────────────────────────────────────────────
            for (ResumeDocumentData.Section section : data.sections()) {
                addSectionHeading(doc, section.title(), profile.accentHex(), fontFamily);
                addSectionBody(doc, section.content(), fontFamily);
            }

            doc.write(out);
        }

        log.info("DOCX generated: {}", filePath);
        return filePath;
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void addSectionHeading(XWPFDocument doc, String title, String accentHex, String fontFamily) {
        XWPFParagraph heading = doc.createParagraph();
        heading.setSpacingBefore(160);
        heading.setSpacingAfter(40);
        // Bottom border on heading paragraph
        CTPPr pPr = heading.getCTP().addNewPPr();
        CTPBdr pBdr = pPr.addNewPBdr();
        CTBorder bottom = pBdr.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(4));
        bottom.setSpace(BigInteger.valueOf(1));

        XWPFRun run = heading.createRun();
        run.setText(title.toUpperCase());
        run.setBold(true);
        run.setFontSize(11);
        run.setFontFamily(fontFamily);
        run.setColor(accentHex);
    }

    private void addSectionBody(XWPFDocument doc, String content, String fontFamily) {
        if (content == null || content.isBlank()) return;
        for (String line : content.split("\n")) {
            XWPFParagraph para = doc.createParagraph();
            para.setSpacingAfter(40);
            XWPFRun run = para.createRun();
            run.setText(line.isBlank() ? "" : line);
            run.setFontSize(10);
            run.setFontFamily(fontFamily);
        }
    }

    private String buildContactLine(ResumeDocumentData data) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (data.email()    != null && !data.email().isBlank())    parts.add(data.email());
        if (data.phone()    != null && !data.phone().isBlank())    parts.add(data.phone());
        if (data.location() != null && !data.location().isBlank()) parts.add(data.location());
        return String.join("  |  ", parts);
    }

    private TemplateProfile profileFor(ResumeDocumentData data) {
        if (data.customColor() != null && data.customColor().matches("#[0-9a-fA-F]{6}")) {
            return new TemplateProfile(data.customColor().substring(1));
        }
        String label = ((data.templateName() == null ? "" : data.templateName()) + " "
                + (data.templateCategory() == null ? "" : data.templateCategory())).toLowerCase();
        if (label.contains("corporate") || label.contains("blue") || label.contains("sales")) return new TemplateProfile("2563EB");
        if (label.contains("technology") || label.contains("software") || label.contains("data")) return new TemplateProfile("0F766E");
        if (label.contains("healthcare") || label.contains("education") || label.contains("entry")) return new TemplateProfile("166534");
        if (label.contains("creative")) return new TemplateProfile("155E75");
        if (label.contains("infographic")) return new TemplateProfile("B45309");
        if (label.contains("management") || label.contains("professional")) return new TemplateProfile("9F6B66");
        return new TemplateProfile("111827");
    }

    private record TemplateProfile(String accentHex) {
    }

    private String docxFont(String requested) {
        if (requested == null || requested.isBlank()) return DEFAULT_FONT;
        return switch (requested.trim()) {
            case "Arial", DEFAULT_FONT, "Georgia", "Times New Roman", "Helvetica" -> requested.trim();
            default -> DEFAULT_FONT;
        };
    }
}
