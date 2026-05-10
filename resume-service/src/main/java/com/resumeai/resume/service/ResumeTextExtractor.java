package com.resumeai.resume.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class ResumeTextExtractor {

    private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file must be 8 MB or smaller");
        }

        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            String text;
            if (fileName.endsWith(".pdf")) {
                text = extractPdf(file);
            } else if (fileName.endsWith(".docx")) {
                text = extractDocx(file);
            } else if (fileName.endsWith(".txt") || fileName.endsWith(".md") || contentType(file).startsWith("text/")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Upload a PDF, DOCX, TXT, or MD resume file");
            }

            String cleaned = text == null ? "" : text.trim();
            if (cleaned.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Could not read text from this resume file");
            }
            return cleaned;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            log.warn("Resume text extraction failed for {}", fileName, ex);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read this resume file", ex);
        }
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell -> text.append(cell.getText()).append('\n'))));
            return text.toString();
        }
    }

    private String contentType(MultipartFile file) {
        String type = file.getContentType();
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }
}
