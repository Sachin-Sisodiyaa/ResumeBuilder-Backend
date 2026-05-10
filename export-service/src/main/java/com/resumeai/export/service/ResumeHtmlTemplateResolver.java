package com.resumeai.export.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds complete XHTML documents from resume data and template metadata.
 *
 * <p>The generated XHTML is compatible with Flying Saucer's CSS 2.1 renderer
 * and produces PDFs that visually match the frontend builder preview.
 *
 * <p>Layout detection and section splitting mirror the Angular builder component
 * ({@code previewTemplateClass}, {@code usesSidebarLayout}, {@code isSidebarSection}).
 */
@Component
public class ResumeHtmlTemplateResolver {

    /** Build a full XHTML document ready for Flying Saucer rendering. */
    public String resolve(ResumeDocumentData data) {
        LayoutType layout = detectLayout(data);
        Palette palette = applyCustomizations(paletteFor(label(data)), data);
        String css = buildCss(palette, layout);
        String body = buildBody(data, palette, layout);
        return wrapDocument(data, css, body);
    }

    // Layout types

    private enum LayoutType { SIDEBAR, STRUCTURED, SINGLE_COLUMN }

    private LayoutType detectLayout(ResumeDocumentData data) {
        String l = label(data);
        if (l.contains("professional") || l.contains("management")
                || l.contains("product manager") || l.contains("project")) {
            return LayoutType.STRUCTURED;
        }
        if (l.contains("blue") || l.contains("studio") || l.contains("technology")
                || l.contains("software") || l.contains("data") || l.contains("creative")
                || l.contains("healthcare") || l.contains("infographic")
                || l.contains("corporate") || l.contains("timeline")
                || l.contains("developer portrait") || l.contains("clinical")) {
            return LayoutType.SIDEBAR;
        }
        return LayoutType.SINGLE_COLUMN;
    }

    // Section splitting â€” mirrors frontend isSidebarSection()

    private boolean isSidebarSection(ResumeDocumentData.Section section) {
        String t = section.title().toLowerCase();
        return t.contains("skill") || t.contains("language") || t.contains("certification")
                || t.contains("details") || t.contains("strength")
                || t.contains("contact") || t.contains("tools");
    }

    private List<ResumeDocumentData.Section> sidebarSections(ResumeDocumentData data) {
        return data.sections().stream().filter(this::isSidebarSection).collect(Collectors.toList());
    }

    private List<ResumeDocumentData.Section> mainSections(ResumeDocumentData data) {
        return data.sections().stream().filter(s -> !isSidebarSection(s)).collect(Collectors.toList());
    }

    // XHTML assembly

    private String wrapDocument(ResumeDocumentData data, String css, String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n"
                + "  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                + "<title>" + esc(data.fullName()) + " Resume</title>\n"
                + "<style type=\"text/css\">\n" + css + "</style>\n"
                + "</head>\n<body>\n" + body + "</body>\n</html>";
    }

    private String buildBody(ResumeDocumentData data, Palette p, LayoutType layout) {
        return switch (layout) {
            case SIDEBAR -> sidebarBody(data, p);
            case STRUCTURED -> structuredBody(data, p);
            case SINGLE_COLUMN -> singleColumnBody(data, p);
        };
    }

    // â”€â”€ Sidebar layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String sidebarBody(ResumeDocumentData data, Palette p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"resume\">\n");

        // Header
        sb.append("  <div class=\"header\">\n");
        sb.append("    <h1>").append(esc(data.fullName())).append("</h1>\n");
        appendSubtitle(sb, data);
        sb.append("    <div class=\"contact\">").append(esc(contactLine(data))).append("</div>\n");
        sb.append("  </div>\n");

        // Body
        sb.append("  <div class=\"body-wrap\">\n");

        // Sidebar column
        sb.append("    <div class=\"sidebar\">\n");
        for (ResumeDocumentData.Section s : sidebarSections(data)) {
            appendSection(sb, s);
        }
        sb.append("    </div>\n");

        // Main column
        sb.append("    <div class=\"main-content\">\n");
        for (ResumeDocumentData.Section s : mainSections(data)) {
            appendSection(sb, s);
        }
        sb.append("    </div>\n");

        sb.append("  </div>\n</div>\n");
        return sb.toString();
    }

    // â”€â”€ Structured layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String structuredBody(ResumeDocumentData data, Palette p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"resume\">\n");

        // Header
        sb.append("  <div class=\"header\">\n");
        sb.append("    <div class=\"contact\">").append(esc(contactLine(data))).append("</div>\n");
        sb.append("    <h1>").append(esc(data.fullName())).append("</h1>\n");
        appendSubtitle(sb, data);
        sb.append("  </div>\n");

        // Sections â€” each rendered as a two-column row (label | content)
        sb.append("  <div class=\"sections\">\n");
        for (ResumeDocumentData.Section s : data.sections()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <div class=\"section-label\"><h2>")
                    .append(esc(s.title())).append("</h2></div>\n");
            sb.append("      <div class=\"section-value\">")
                    .append(contentHtml(s.content())).append("</div>\n");
            sb.append("    </div>\n");
        }
        sb.append("  </div>\n</div>\n");
        return sb.toString();
    }

    // â”€â”€ Single-column layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String singleColumnBody(ResumeDocumentData data, Palette p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"resume\">\n");

        sb.append("  <div class=\"header\">\n");
        sb.append("    <h1>").append(esc(data.fullName())).append("</h1>\n");
        String contact = contactLine(data);
        if (!contact.isBlank()) {
            sb.append("    <div class=\"contact\">").append(esc(contact)).append("</div>\n");
        }
        appendSubtitle(sb, data);
        sb.append("  </div>\n");

        for (ResumeDocumentData.Section s : data.sections()) {
            appendSection(sb, s);
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    // â”€â”€ Shared HTML helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void appendSection(StringBuilder sb, ResumeDocumentData.Section s) {
        sb.append("      <div class=\"section\">\n");
        sb.append("        <h2>").append(esc(s.title())).append("</h2>\n");
        sb.append("        ").append(contentHtml(s.content())).append("\n");
        sb.append("      </div>\n");
    }

    private void appendSubtitle(StringBuilder sb, ResumeDocumentData data) {
        if (data.targetJobTitle() != null && !data.targetJobTitle().isBlank()) {
            sb.append("    <p class=\"subtitle\">").append(esc(data.targetJobTitle())).append("</p>\n");
        }
    }

    /** Converts plain-text content into simple XHTML blocks suitable for PDF rendering. */
    private String contentHtml(String content) {
        if (content == null || content.isBlank()) return "<p></p>";

        StringBuilder html = new StringBuilder("<div class=\"section-content\">");
        boolean inList = false;
        for (String rawLine : content.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                if (inList) {
                    html.append("</ul>");
                    inList = false;
                }
                continue;
            }

            String bulletText = bulletText(line);
            if (bulletText != null) {
                if (!inList) {
                    html.append("<ul>");
                    inList = true;
                }
                html.append("<li>").append(esc(bulletText)).append("</li>");
                continue;
            }

            if (inList) {
                html.append("</ul>");
                inList = false;
            }
            if (looksLikeInlineHeading(line)) {
                html.append("<p class=\"content-heading\">").append(esc(line)).append("</p>");
            } else {
                html.append("<p>").append(esc(line)).append("</p>");
            }
        }
        if (inList) {
            html.append("</ul>");
        }
        html.append("</div>");
        return html.toString();
    }

    private String bulletText(String line) {
        if (line.startsWith("- ") || line.startsWith("* ")) {
            return line.substring(2).trim();
        }
        if (line.startsWith("\u2022 ")) {
            return line.substring(2).trim();
        }
        return null;
    }

    private boolean looksLikeInlineHeading(String line) {
        if (line.length() < 4 || line.length() > 48) {
            return false;
        }
        boolean hasLetter = line.chars().anyMatch(Character::isLetter);
        return hasLetter && line.equals(line.toUpperCase()) && !line.endsWith(".");
    }

    // CSS generation

    private String buildCss(Palette p, LayoutType layout) {
        StringBuilder css = new StringBuilder();
        css.append("@page { size: A4; margin: 12mm; }\n");
        css.append("* { box-sizing: border-box; }\n");
        css.append("body { margin: 0; padding: 0; font-family: ").append(p.fontFamily)
                .append("; color: ").append(p.textColor).append("; font-size: 9.5pt; }\n");
        css.append(".resume { width: 100%; background: #fff; page-break-after: avoid; }\n");
        css.append(".section { page-break-inside: avoid; }\n");
        css.append("p { orphans: 2; widows: 2; margin: 0 0 2.5pt; }\n");
        css.append("ul { margin: 2pt 0 3pt 12pt; padding: 0; }\n");
        css.append("li { margin: 0 0 2pt; padding: 0; }\n");
        css.append(".content-heading { font-weight: bold; text-transform: uppercase; margin-top: 4pt; }\n");

        switch (layout) {
            case SIDEBAR -> appendSidebarCss(css, p);
            case STRUCTURED -> appendStructuredCss(css, p);
            case SINGLE_COLUMN -> appendSingleColumnCss(css, p);
        }
        return css.toString();
    }

    private void appendSidebarCss(StringBuilder css, Palette p) {
        // Header
        css.append(".header { background: ").append(p.headerBg)
                .append("; color: ").append(p.headerText)
                .append("; padding: 18pt 24pt; }\n");
        css.append(".header h1 { font-size: 19.5pt; margin: 0 0 3pt; text-transform: uppercase; ")
                .append("letter-spacing: 0.04em; color: ").append(p.headerText).append("; }\n");
        css.append(".subtitle { margin: 3pt 0 0; font-size: 8.5pt; font-weight: 600; color: ")
                .append(p.subtitleColor).append("; }\n");
        css.append(".contact { font-size: 7.6pt; margin-top: 4pt; color: ")
                .append(p.contactColor).append("; }\n");

        // Body wrapper
        css.append(".body-wrap { overflow: hidden; }\n");

        // Sidebar column (float left)
        css.append(".sidebar { float: left; width: 34%; min-height: 540pt; padding: 14pt 12pt; ")
                .append("background: ").append(p.sidebarBg).append("; ");
        if (p.sidebarBorder != null) {
            css.append("border-right: ").append(p.sidebarBorder).append("; ");
        }
        css.append("}\n");

        // Main column
        css.append(".main-content { margin-left: 34%; padding: 14pt 18pt; }\n");

        // Sections
        css.append(".section { margin-bottom: 8pt; }\n");
        css.append(".section h2 { font-size: 8pt; letter-spacing: 0.08em; text-transform: uppercase; ")
                .append("color: ").append(p.accent).append("; margin: 0 0 4pt; padding: 0; }\n");
        css.append(".section p, .section li { font-size: 7.7pt; line-height: 1.34; }\n");
    }

    private void appendStructuredCss(StringBuilder css, Palette p) {
        // Header â€” pink/salmon bg
        css.append(".header { background: ").append(p.headerBg)
                .append("; border-bottom: 1px solid #4b5563; padding: 13pt 24pt; text-align: center; }\n");
        css.append(".header h1 { font-size: 16.5pt; font-weight: 500; letter-spacing: 0.04em; ")
                .append("text-transform: uppercase; margin: 0 0 3pt; color: ").append(p.textColor).append("; }\n");
        css.append(".subtitle { margin: 0; font-size: 8.5pt; }\n");
        css.append(".contact { font-size: 7.6pt; margin-bottom: 7pt; color: #263244; }\n");

        // Sections â€” each as a two-column row using float
        css.append(".sections { padding: 9pt 24pt 21pt; }\n");
        css.append(".section { border-top: 1px solid #8c95a1; padding: 7pt 0; overflow: hidden; }\n");
        css.append(".section-label { float: left; width: 24%; }\n");
        css.append(".section-label h2 { border-top: 2pt solid ").append(p.accentLight)
                .append("; font-size: 7.8pt; letter-spacing: 0.02em; text-transform: uppercase; ")
                .append("margin: 0; padding-top: 3pt; }\n");
        css.append(".section-value { margin-left: 28%; }\n");
        css.append(".section-value p, .section-value li { font-size: 7.8pt; line-height: 1.36; }\n");
    }

    private void appendSingleColumnCss(StringBuilder css, Palette p) {
        css.append(".resume { padding: 0; }\n");

        // Header
        if (p.centerHeader) {
            css.append(".header { text-align: center; border-bottom: 2px solid ")
                    .append(p.accent).append("; padding-bottom: 8pt; margin-bottom: 10pt; }\n");
        } else {
            css.append(".header { border-bottom: 2px solid ").append(p.accent)
                    .append("; padding-bottom: 8pt; margin-bottom: 10pt; }\n");
        }
        css.append(".header h1 { font-size: 18pt; margin: 0 0 3pt; color: ").append(p.textColor).append("; }\n");
        css.append(".subtitle { font-size: 8.6pt; font-style: italic; margin: 0; color: #334155; }\n");
        css.append(".contact { font-size: 7.8pt; margin-top: 3pt; color: #4b5563; }\n");

        // Sections
        css.append(".section { margin-bottom: 8pt; }\n");
        css.append(".section h2 { border-bottom: 1px solid ").append(p.accent)
                .append("; font-size: 8.4pt; text-transform: uppercase; margin: 0 0 4pt; padding-bottom: 2.5pt; ");
        if (p.centerHeader) {
            css.append("text-align: center; ");
        }
        css.append("color: ").append(p.accent).append("; }\n");
        css.append(".section p, .section li { font-size: 8.2pt; line-height: 1.36; }\n");
    }

    // Palette (accent colours, backgrounds, fonts) per template

    private record Palette(
            String accent, String accentLight,
            String headerBg, String headerText, String subtitleColor, String contactColor,
            String sidebarBg, String sidebarBorder,
            String textColor, String fontFamily, boolean centerHeader) {
    }

    private Palette paletteFor(String label) {
        // Creative Studio
        if (label.contains("creative")) {
            return new Palette("#155e75", "#cffafe",
                    "#155e75", "#ffffff", "#cffafe", "#cffafe",
                    "#f0fdfa", null,
                    "#0f172a", "Arial, Helvetica, sans-serif", false);
        }
        // Infographic Pro
        if (label.contains("infographic")) {
            return new Palette("#f59e0b", "#fef3c7",
                    "#111827", "#ffffff", "#cbd5e1", "#cbd5e1",
                    "#f1f5f9", "6px solid #f59e0b",
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Blue Corporate / Professional Blue
        if (label.contains("corporate") || label.contains("blue")) {
            return new Palette("#2563eb", "#dbeafe",
                    "#ffffff", "#1d4ed8", "#334155", "#4b5563",
                    "#f8fbff", "3px solid #2563eb",
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Healthcare / Clinical
        if (label.contains("healthcare") || label.contains("clinical")) {
            return new Palette("#166534", "#dcfce7",
                    "#ffffff", "#14532d", "#334155", "#4b5563",
                    "#f0fdf4", "5px solid #16a34a",
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Software Engineer / Developer
        if (label.contains("software") || label.contains("technology") || label.contains("developer")) {
            return new Palette("#111827", "#e5e7eb",
                    "#ffffff", "#111827", "#334155", "#4b5563",
                    "#ffffff", "1px solid #cbd5e1",
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Data Analyst
        if (label.contains("data")) {
            return new Palette("#0891b2", "#cffafe",
                    "#164e63", "#ffffff", "#cffafe", "#cffafe",
                    "#ecfeff", "8px solid #0891b2",
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Timeline Marketing
        if (label.contains("timeline") || label.contains("marketing")) {
            return new Palette("#334155", "#d1d5db",
                    "#ffffff", "#334155", "#334155", "#4b5563",
                    "#ffffff", "2px solid #d1d5db",
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Sales
        if (label.contains("sales")) {
            return new Palette("#1d4ed8", "#dbeafe",
                    "#ffffff", "#1d4ed8", "#334155", "#4b5563",
                    "#ffffff", null,
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Product Manager / Professional (structured)
        if (label.contains("product") || label.contains("professional") || label.contains("management")) {
            return new Palette("#64748b", "#f2d1cf",
                    "#f7d9d7", "#111827", "#334155", "#263244",
                    "#ffffff", null,
                    "#111827", "Arial, Helvetica, sans-serif", true);
        }
        // Executive Suite
        if (label.contains("executive")) {
            return new Palette("#1f2937", "#e5e7eb",
                    "#ffffff", "#111827", "#374151", "#374151",
                    "#ffffff", null,
                    "#111827", "Georgia, 'Times New Roman', serif", true);
        }
        // Classic / Academic
        if (label.contains("classic") || label.contains("academic")) {
            return new Palette("#334155", "#e2e8f0",
                    "#ffffff", "#111827", "#334155", "#334155",
                    "#ffffff", null,
                    "#111827", "Georgia, 'Times New Roman', serif", false);
        }
        // Modern ATS / ATS
        if (label.contains("ats") || label.contains("harvard")) {
            return new Palette("#111827", "#e5e7eb",
                    "#ffffff", "#111827", "#374151", "#536170",
                    "#ffffff", null,
                    "#111827", "'Times New Roman', Georgia, serif", true);
        }
        // Compact / Fresher
        if (label.contains("compact") || label.contains("fresher") || label.contains("entry")) {
            return new Palette("#0f766e", "#99f6e4",
                    "#ffffff", "#111827", "#0f766e", "#64748b",
                    "#ffffff", null,
                    "#172033", "Arial, Helvetica, sans-serif", false);
        }
        // Minimal
        if (label.contains("minimal")) {
            return new Palette("#111827", "#d1d5db",
                    "#ffffff", "#111827", "#334155", "#536170",
                    "#ffffff", null,
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Teacher
        if (label.contains("teacher") || label.contains("education")) {
            return new Palette("#15803d", "#86efac",
                    "#ffffff", "#111827", "#334155", "#4b5563",
                    "#ffffff", null,
                    "#111827", "'Times New Roman', Georgia, serif", false);
        }
        // Engineering / Process
        if (label.contains("engineering") || label.contains("process")) {
            return new Palette("#111827", "#e5e7eb",
                    "#ffffff", "#111827", "#334155", "#4b5563",
                    "#ffffff", null,
                    "#111827", "Arial, Helvetica, sans-serif", false);
        }
        // Default â€” teal
        return new Palette("#0f766e", "#99f6e4",
                "#ffffff", "#111827", "#0f766e", "#4b5563",
                "#ffffff", null,
                "#111827", "Arial, Helvetica, sans-serif", false);
    }

    private Palette applyCustomizations(Palette p, ResumeDocumentData data) {
        String font = safeFont(data.customFont(), p.fontFamily());
        String color = safeColor(data.customColor(), p.accent());
        if (font.equals(p.fontFamily()) && color.equals(p.accent())) {
            return p;
        }
        return new Palette(color, color,
                p.headerBg(), p.headerText(), p.subtitleColor(), p.contactColor(),
                p.sidebarBg(), p.sidebarBorder(), p.textColor(), font, p.centerHeader());
    }

    private String safeFont(String requested, String fallback) {
        if (requested == null || requested.isBlank()) {
            return fallback;
        }
        String normalized = requested.trim();
        return switch (normalized) {
            case "Arial", "Calibri", "Georgia", "Times New Roman", "Helvetica" ->
                    "'" + normalized.replace("'", "") + "', Arial, sans-serif";
            default -> fallback;
        };
    }

    private String safeColor(String requested, String fallback) {
        if (requested == null) {
            return fallback;
        }
        String normalized = requested.trim();
        return normalized.matches("#[0-9a-fA-F]{6}") ? normalized : fallback;
    }

    // Helpers

    private String label(ResumeDocumentData data) {
        return ((data.templateName() == null ? "" : data.templateName()) + " "
                + (data.templateCategory() == null ? "" : data.templateCategory())).toLowerCase();
    }

    private String contactLine(ResumeDocumentData data) {
        List<String> parts = new ArrayList<>();
        if (data.phone() != null && !data.phone().isBlank()) parts.add(data.phone());
        if (data.email() != null && !data.email().isBlank()) parts.add(data.email());
        if (data.location() != null && !data.location().isBlank()) parts.add(data.location());
        return String.join("  \u00B7  ", parts);
    }

    /** XML-safe escaping for text content. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
