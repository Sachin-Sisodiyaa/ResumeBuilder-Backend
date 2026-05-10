package com.resumeai.template.service;

import com.resumeai.template.dto.TemplateDtos.TemplateRequest;
import com.resumeai.template.model.ResumeTemplate;
import com.resumeai.template.repository.TemplateRepository;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/** Template service for CRUD, seeding, and preview rendering. */
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final SpringTemplateEngine templateEngine = previewTemplateEngine();

    @PostConstruct
    void init() {
        seedTemplates();
    }
    @Override
    public ResumeTemplate createTemplate(TemplateRequest request) {
        ResumeTemplate t = new ResumeTemplate();
        t.setName(request.name());
        t.setDescription(request.description());
        t.setThumbnailUrl(request.thumbnailUrl());
        t.setHtmlLayout(request.htmlLayout());
        t.setCssStyles(request.cssStyles());
        t.setCategory(request.category());
        t.setPremium(Boolean.TRUE.equals(request.premium()));
        t.setActive(request.active() == null || request.active());
        t.setUsageCount(0);
        return templateRepository.save(t);
    }

    @Override
    public ResumeTemplate getTemplateById(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Template not found: " + templateId));
    }

    @Override
    public List<ResumeTemplate> getTemplates(Boolean premium, String category, boolean activeOnly) {
        // Optimised paths using indexed JPA queries for the most common filter combos
        if (activeOnly && premium == null && category == null) {
            return templateRepository.findByActiveTrueOrderByUsageCountDesc();
        }
        if (activeOnly && category != null && premium == null) {
            return templateRepository.findByCategoryAndActiveTrue(category);
        }
        if (activeOnly && premium != null && category == null) {
            return templateRepository.findByPremiumAndActiveTrue(premium);
        }
        // Fallback: load and filter in Java for rare multi-filter admin queries
        return templateRepository.findAll().stream()
                .filter(t -> premium  == null || premium.equals(t.isPremium()))
                .filter(t -> category == null || category.equalsIgnoreCase(t.getCategory()))
                .filter(t -> !activeOnly || t.isActive())
                .sorted(Comparator.comparingLong(ResumeTemplate::getUsageCount).reversed())
                .toList();
    }

    @Override
    public ResumeTemplate updateTemplate(Long templateId, TemplateRequest request) {
        ResumeTemplate t = getTemplateById(templateId);
        if (request.name()         != null) t.setName(request.name());
        if (request.description()  != null) t.setDescription(request.description());
        if (request.thumbnailUrl() != null) t.setThumbnailUrl(request.thumbnailUrl());
        if (request.htmlLayout()   != null) t.setHtmlLayout(request.htmlLayout());
        if (request.cssStyles()    != null) t.setCssStyles(request.cssStyles());
        if (request.category()     != null) t.setCategory(request.category());
        if (request.premium()      != null) t.setPremium(request.premium());
        if (request.active()       != null) t.setActive(request.active());
        return templateRepository.save(t);
    }

    @Override
    public ResumeTemplate deactivateTemplate(Long templateId) {
        ResumeTemplate t = getTemplateById(templateId);
        t.setActive(false);
        return templateRepository.save(t);
    }

    @Override
    public void deleteTemplate(Long templateId) {
        getTemplateById(templateId);
        templateRepository.deleteById(templateId);
    }

    @Override
    public ResumeTemplate incrementUsage(Long templateId) {
        ResumeTemplate t = getTemplateById(templateId);
        t.setUsageCount(t.getUsageCount() + 1);
        return templateRepository.save(t);
    }

    @Override
    public List<ResumeTemplate> getPopularTemplates() {
        // Use indexed JPA query; limit in Java (only ~16 templates total)
        List<ResumeTemplate> active = templateRepository.findByActiveTrueOrderByUsageCountDesc();
        return active.size() <= 6 ? active : active.subList(0, 6);
    }

    @Override
    public String renderPreview(Long templateId) {
        ResumeTemplate template = getTemplateById(templateId);
        Map<String, Object> sample = samplePreviewData();
        Context context = new Context();
        context.setVariables(sample);

        TemplateDesign design = previewDesignFor(template);
        String html = injectStyles(blankToDefault(design.html(), template.getName()), design.css());
        String rendered = templateEngine.process(html, context);
        for (Map.Entry<String, Object> entry : sample.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }

    /** Admin analytics: usage count per template. */
    public Map<String, Long> getUsageStats() {
        return templateRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ResumeTemplate::getName,
                        t -> (long) t.getUsageCount(),
                        Long::sum));
    }

    private void seedTemplates() {
        Set<String> existingNames = templateRepository.findAll().stream()
                .map(ResumeTemplate::getName)
                .collect(Collectors.toSet());
        deleteRetiredTemplates(existingNames);
        seedIfMissing(existingNames, "Template A",
             "Pre-designed ATS-friendly HTML/CSS resume layout with clean typography and section hierarchy.",
             "/thumbs/template-a.png", "ATS_OPTIMISED", false, 329);

        seedIfMissing(existingNames, "Classic",
             "Timeless single-column layout. Best for corporate and government roles.",
             "/thumbs/classic.png", "CLASSIC", false, 210);

        seedIfMissing(existingNames, "Professional Blue",
             "Clean two-column layout with accent colours. Ideal for finance and consulting.",
             "/thumbs/pro-blue.png", "PROFESSIONAL", false, 185);

        seedIfMissing(existingNames, "Minimal",
             "Ultra-clean whitespace-first design. Suits tech and design professionals.",
             "/thumbs/minimal.png", "MINIMAL", false, 160);

        seedIfMissing(existingNames, "Modern ATS",
             "ATS-optimised layout with keyword-rich section ordering.",
             "/thumbs/modern-ats.png", "ATS_OPTIMISED", false, 320);

        seedIfMissing(existingNames, "Compact",
             "One-page layout optimised for candidates with diverse experience.",
             "/thumbs/compact.png", "MODERN", false, 145);

        seedIfMissing(existingNames, "Software Engineer",
             "Ready-to-use engineering resume with projects, impact bullets, GitHub, and technical skills.",
             "/thumbs/software-engineer.png", "TECHNOLOGY", false, 285);

        seedIfMissing(existingNames, "Fresher Starter",
             "Beginner-friendly layout for internships, campus placements, projects, and certifications.",
             "/thumbs/fresher-starter.png", "ENTRY_LEVEL", false, 260);

        seedIfMissing(existingNames, "Data Analyst",
             "Analytics resume with dashboard projects, tools, metrics, SQL, Python, and BI sections.",
             "/thumbs/data-analyst.png", "DATA", false, 235);

        seedIfMissing(existingNames, "Sales Growth",
             "Outcome-focused resume for sales, business development, revenue targets, and client wins.",
             "/thumbs/sales-growth.png", "SALES", false, 132);

        seedIfMissing(existingNames, "Teacher Profile",
             "Structured education resume for teaching roles, lesson planning, outcomes, and credentials.",
             "/thumbs/teacher-profile.png", "EDUCATION", false, 118);

        seedIfMissing(existingNames, "Healthcare Professional",
             "Clear healthcare layout for clinical experience, licenses, patient care, and compliance.",
             "/thumbs/healthcare-professional.png", "HEALTHCARE", false, 104);

        seedIfMissing(existingNames, "Process Engineer Dense",
             "Dense black-and-white engineering resume inspired by process engineer ATS formats.",
             "/thumbs/process-engineer-dense.png", "ENGINEERING", false, 142);

        seedIfMissing(existingNames, "Academic CV Serif",
             "Traditional serif academic CV with compact education, research, leadership, and activities sections.",
             "/thumbs/academic-cv-serif.png", "ACADEMIC", false, 126);

        seedIfMissing(existingNames, "Developer Portrait",
             "Modern web developer resume with portrait-style header, sidebar contact, skills, and education.",
             "/thumbs/developer-portrait.png", "TECHNOLOGY", false, 168);

        seedIfMissing(existingNames, "Balanced Professional",
             "Clean centered professional resume with blue section accents and balanced spacing.",
             "/thumbs/balanced-professional.png", "PROFESSIONAL", false, 151);

        seedIfMissing(existingNames, "Timeline Marketing",
             "Timeline-based marketing resume with side contact, language, references, and work milestones.",
             "/thumbs/timeline-marketing.png", "MARKETING", false, 119);

        seedIfMissing(existingNames, "Blue Corporate Split",
             "Corporate two-column resume with strong blue rules, skills, strengths, and languages.",
             "/thumbs/blue-corporate-split.png", "CORPORATE", false, 137);
        seedIfMissing(existingNames, "Creative Studio",
             "Bold typography, infographic sidebar. Perfect for designers and marketers.",
             "/thumbs/creative.png", "CREATIVE", true, 94);

        seedIfMissing(existingNames, "Executive Suite",
             "Premium serif typeface, full-bleed header. Tailored for C-level executives.",
             "/thumbs/executive.png", "EXECUTIVE", true, 78);

        seedIfMissing(existingNames, "Infographic Pro",
             "Visual skills bars, timeline work history. Stand out in competitive markets.",
             "/thumbs/infographic.png", "INFOGRAPHIC", true, 63);

        seedIfMissing(existingNames, "Product Manager Pro",
             "Premium product resume with roadmap impact, launches, stakeholder leadership, and metrics.",
             "/thumbs/product-manager-pro.png", "MANAGEMENT", true, 88);

        seedIfMissing(existingNames, "Senior Project Lead",
             "Premium project leadership template for delivery, budgets, teams, risks, and outcomes.",
             "/thumbs/senior-project-lead.png", "MANAGEMENT", true, 72);
    }

    private void deleteRetiredTemplates(Set<String> existingNames) {
        Set<String> seededNames = defaultSeedNames();
        templateRepository.findAll().stream()
                .filter(template -> !seededNames.contains(template.getName()))
                .forEach(template -> {
                    templateRepository.delete(template);
                    existingNames.remove(template.getName());
                });
    }

    private static Set<String> defaultSeedNames() {
        return Set.of(
                "Template A", "Classic", "Professional Blue", "Minimal", "Modern ATS", "Compact",
                "Software Engineer", "Fresher Starter", "Data Analyst", "Sales Growth", "Teacher Profile",
                "Healthcare Professional", "Process Engineer Dense", "Academic CV Serif",
                "Developer Portrait", "Balanced Professional", "Timeline Marketing", "Blue Corporate Split",
                "Creative Studio", "Executive Suite", "Infographic Pro",
                "Product Manager Pro", "Senior Project Lead");
    }

    private void seedIfMissing(Set<String> existingNames, String name, String desc, String thumb,
                               String category, boolean premium, int initialUsage) {
        if (!defaultSeedNames().contains(name)) {
            existingNames.remove(name);
            return;
        }
        if (existingNames.contains(name)) {
            refreshSeededTemplate(name, desc, thumb, category, premium);
            return;
        }
        seed(name, desc, thumb, category, premium, initialUsage);
        existingNames.add(name);
    }

    private void seed(String name, String desc, String thumb,
                      String category, boolean premium, int initialUsage) {
        TemplateDesign design = designFor(name, category);

        ResumeTemplate t = new ResumeTemplate();
        t.setName(name);
        t.setDescription(desc);
        t.setThumbnailUrl(thumbnailFor(name, category));
        t.setHtmlLayout(design.html());
        t.setCssStyles(design.css());
        t.setCategory(category);
        t.setPremium(premium);
        t.setActive(true);
        t.setUsageCount(initialUsage);
        templateRepository.save(t);
    }

    private void refreshSeededTemplate(String name, String desc, String thumb, String category, boolean premium) {
        templateRepository.findAll().stream()
                .filter(template -> name.equals(template.getName()))
                .findFirst()
                .ifPresent(template -> {
                    TemplateDesign design = designFor(name, category);
                    template.setDescription(desc);
                    template.setThumbnailUrl(thumbnailFor(name, category));
                    template.setHtmlLayout(design.html());
                    template.setCssStyles(design.css());
                    template.setCategory(category);
                    template.setPremium(premium);
                    templateRepository.save(template);
                });
    }

    private static String thumbnailFor(String name, String category) {
        String slug = (category + "-" + name)
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        return "/thumbs/seeded/" + slug + ".svg";
    }

    private static SpringTemplateEngine previewTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static String blankToDefault(String htmlLayout, String templateName) {
        if (htmlLayout != null && !htmlLayout.isBlank()) {
            return htmlLayout;
        }
        return templateAHtml(templateName == null ? "Resume" : templateName);
    }

    private static TemplateDesign previewDesignFor(ResumeTemplate template) {
        String name = template.getName();
        boolean hasCustomMarkup = template.getHtmlLayout() != null && !template.getHtmlLayout().isBlank();
        boolean hasCustomStyles = template.getCssStyles() != null && !template.getCssStyles().isBlank();
        if (!hasCustomMarkup && !hasCustomStyles && name != null && defaultSeedNames().contains(name)) {
            return designFor(name, template.getCategory());
        }
        return new TemplateDesign(template.getHtmlLayout(), template.getCssStyles());
    }

    private static String injectStyles(String html, String css) {
        String effectiveCss = (css == null || css.isBlank()) ? templateACss() : css;
        String styleBlock = "<style>" + previewShellCss() + effectiveCss + "</style>";
        if (html.toLowerCase().contains("</head>")) {
            return html.replaceFirst("(?i)</head>", styleBlock + "</head>");
        }
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">" + styleBlock
                + "</head><body>" + html + "</body></html>";
    }

    private static String previewShellCss() {
        return "*,*::before,*::after{box-sizing:border-box}"
                + "html{background:#eef3f7}"
                + "body{min-height:100vh}"
                + "body,h1,h2,p,ul{margin-top:0}";
    }

    private static Map<String, Object> samplePreviewData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", "Aarav Sharma");
        data.put("jobTitle", "Software Engineer");
        data.put("email", "aarav.sharma@example.com");
        data.put("phone", "555-555-5555");
        data.put("location", "New York, NY");
        data.put("summary", "Experienced professional with measurable impact, strong communication, and role-focused achievements.");
        data.put("experience", "Amazon - Technical Product Manager\nLed product roadmap and improved customer workflows.");
        data.put("education", "Stevens Institute of Technology\nMaster of Business Administration");
        data.put("skills", "Leadership, Analytics, Communication, SQL");
        return data;
    }

    private record TemplateDesign(String html, String css) {
    }

    @Generated
    private static TemplateDesign designFor(String name, String category) {
        String key = (name + " " + category).toLowerCase();
        String exact = name.toLowerCase();
        if (exact.contains("template a")) return new TemplateDesign(namedHtml(name, "ats-dense"), namedCss("ats-dense"));
        if (exact.contains("modern ats")) return new TemplateDesign(namedHtml(name, "harvard"), namedCss("harvard"));
        if (exact.contains("classic")) return new TemplateDesign(namedHtml(name, "classic-serif"), namedCss("classic-serif"));
        if (exact.contains("professional blue")) return new TemplateDesign(namedHtml(name, "blue-two-column"), namedCss("blue-two-column"));
        if (exact.contains("minimal")) return new TemplateDesign(namedHtml(name, "minimal-left"), namedCss("minimal-left"));
        if (exact.contains("compact")) return new TemplateDesign(namedHtml(name, "compact-bars"), namedCss("compact-bars"));
        if (exact.contains("software engineer")) return new TemplateDesign(namedHtml(name, "developer-sidebar"), namedCss("developer-sidebar"));
        if (exact.contains("fresher")) return new TemplateDesign(namedHtml(name, "campus-clean"), namedCss("campus-clean"));
        if (exact.contains("data analyst")) return new TemplateDesign(namedHtml(name, "analytics-grid"), namedCss("analytics-grid"));
        if (exact.contains("sales")) return new TemplateDesign(namedHtml(name, "sales-blue"), namedCss("sales-blue"));
        if (exact.contains("teacher")) return new TemplateDesign(namedHtml(name, "academic"), namedCss("academic"));
        if (exact.contains("healthcare")) return new TemplateDesign(namedHtml(name, "clinical"), namedCss("clinical"));
        if (exact.contains("creative")) return new TemplateDesign(namedHtml(name, "creative-photo"), namedCss("creative-photo"));
        if (exact.contains("executive")) return new TemplateDesign(namedHtml(name, "executive-serif"), namedCss("executive-serif"));
        if (exact.contains("infographic")) return new TemplateDesign(namedHtml(name, "infographic-metrics"), namedCss("infographic-metrics"));
        if (exact.contains("product manager")) return new TemplateDesign(namedHtml(name, "product-structured"), namedCss("product-structured"));
        if (exact.contains("senior project")) return new TemplateDesign(namedHtml(name, "timeline-lead"), namedCss("timeline-lead"));
        if (exact.contains("process engineer dense")) return new TemplateDesign(namedHtml(name, "faith-process"), namedCss("faith-process"));
        if (exact.contains("academic cv serif")) return new TemplateDesign(namedHtml(name, "academic-cv"), namedCss("academic-cv"));
        if (exact.contains("developer portrait")) return new TemplateDesign(namedHtml(name, "developer-portrait"), namedCss("developer-portrait"));
        if (exact.contains("balanced professional")) return new TemplateDesign(namedHtml(name, "jonathan-balanced"), namedCss("jonathan-balanced"));
        if (exact.contains("timeline marketing")) return new TemplateDesign(namedHtml(name, "marketing-timeline"), namedCss("marketing-timeline"));
        if (exact.contains("blue corporate split")) return new TemplateDesign(namedHtml(name, "john-blue"), namedCss("john-blue"));
        if (key.contains("professional") || key.contains("management") || key.contains("project")) {
            return new TemplateDesign(structuredHtml(name), structuredCss());
        }
        if (key.contains("software") || key.contains("data") || key.contains("technology")) {
            return new TemplateDesign(sidebarHtml(name), sidebarCss());
        }
        if (key.contains("minimal") || key.contains("classic") || key.contains("teacher") || key.contains("healthcare")) {
            return new TemplateDesign(classicHtml(name), classicCss(key.contains("minimal")));
        }
        if (key.contains("creative")) {
            return new TemplateDesign(creativeHtml(name), creativeCss());
        }
        if (key.contains("executive")) {
            return new TemplateDesign(executiveHtml(name), executiveCss());
        }
        if (key.contains("infographic")) {
            return new TemplateDesign(infographicHtml(name), infographicCss());
        }
        if (key.contains("compact") || key.contains("sales") || key.contains("fresher")) {
            return new TemplateDesign(compactHtml(name), compactCss());
        }
        return new TemplateDesign(templateAHtml(name), templateACss());
    }

    private static String namedHtml(String name, String layoutClass) {
        return "<!DOCTYPE html><html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + name + "</title></head><body><main class=\"resume " + layoutClass + "\">"
                + "<header><h1 th:text=\"${fullName}\">Aarav Sharma</h1><p th:text=\"${jobTitle}\">Software Engineer</p>"
                + "<small><span th:text=\"${phone}\">Phone</span> · <span th:text=\"${email}\">Email</span> · <span th:text=\"${location}\">Location</span></small></header>"
                + "<div class=\"content\"><aside><section><h2>Contact</h2><p th:text=\"${email}\">Email</p><p th:text=\"${phone}\">Phone</p><p th:text=\"${location}\">Location</p></section>"
                + "<section><h2>Skills</h2><p th:text=\"${skills}\">Skills</p></section></aside>"
                + "<article><section><h2>Summary</h2><p th:text=\"${summary}\">Summary</p></section>"
                + "<section><h2>Professional Experience</h2><p th:text=\"${experience}\">Experience</p></section>"
                + "<section><h2>Education</h2><p th:text=\"${education}\">Education</p></section></article></div>"
                + "</main></body></html>";
    }

    private static String namedCss(String layout) {
        String base = "body{margin:0;background:#eef2f7;color:#111827;font-family:Arial,sans-serif}"
                + ".resume{width:790px;min-height:1040px;margin:0 auto;background:#fff;box-sizing:border-box;padding:46px}"
                + "h1{margin:0}header p{margin:6px 0}small{color:#536170;font-size:12px}.content{display:grid;gap:24px}"
                + "aside{display:none}section{margin-bottom:18px}h2{margin:0 0 8px;font-size:14px;text-transform:uppercase}p{font-size:13px;line-height:1.48;margin:0;white-space:pre-line}";
        return switch (layout) {
            case "ats-dense" -> base + "header{text-align:center;border-bottom:2px solid #222;padding-bottom:14px;margin-bottom:16px}"
                    + "h1{font-family:Georgia,serif;font-size:26px}.content{display:block}h2{text-align:center;border-bottom:1px solid #333;font-family:Georgia,serif}";
            case "harvard" -> base + ".resume{font-family:'Times New Roman',serif;padding:38px 52px}header{text-align:center;border-bottom:1px solid #111;padding-bottom:8px}"
                    + "h1{font-size:24px}h2{border-bottom:1px solid #111;font-size:13px;text-align:left}p{font-size:12px;line-height:1.35}";
            case "classic-serif" -> base + ".resume{font-family:Georgia,serif;padding:54px}header{border-bottom:2px solid #334155;padding-bottom:14px}"
                    + "h1{font-size:24px}h2{border-bottom:1px solid #334155;text-transform:none;font-size:16px}";
            case "blue-two-column" -> base + ".resume{padding:38px}.content{grid-template-columns:1fr 260px}.content aside{display:block;grid-column:2;grid-row:1;background:#f8fbff;border-left:4px solid #2563eb;padding:18px}.content article{grid-column:1;grid-row:1}"
                    + "header{border-bottom:3px solid #2563eb;margin-bottom:22px}h1{color:#2563eb;font-size:32px}h2{color:#1d4ed8;border-bottom:2px solid #2563eb}";
            case "minimal-left" -> base + ".resume{font-family:Inter,Arial,sans-serif;padding:54px}header{text-align:left;border-bottom:1px solid #d1d5db;padding-bottom:24px}"
                    + "h1{font-size:42px;line-height:.95;max-width:260px}h2{display:inline-block;border-bottom:2px solid #111827;letter-spacing:.1em}";
            case "compact-bars" -> base + ".resume{padding:34px 44px;min-height:940px}.content{display:block}header{display:grid;grid-template-columns:1fr auto;border-bottom:5px solid #0f766e;padding-bottom:12px}"
                    + "h1{font-size:30px}header p{color:#0f766e;font-weight:700}h2{color:#0f766e;letter-spacing:.16em}p{font-size:12px;line-height:1.38}";
            case "developer-sidebar" -> base + ".resume{padding:44px}.content{grid-template-columns:185px 1fr}.content aside{display:block;border-right:1px solid #cbd5e1;padding-right:22px}"
                    + "header{border-bottom:1px solid #cbd5e1;padding-bottom:24px;margin-bottom:24px}h1{font-size:42px;line-height:.9;text-transform:uppercase}h2{display:inline-block;border-bottom:3px solid #111827}";
            case "campus-clean" -> base + ".resume{padding:42px 50px}.content{display:block}header{background:#f8fafc;border-left:7px solid #22c55e;padding:18px;margin-bottom:22px}"
                    + "h1{font-size:30px}h2{color:#15803d;border-bottom:1px solid #86efac}";
            case "analytics-grid" -> base + ".resume{padding:36px}.content{grid-template-columns:230px 1fr}.content aside{display:block;background:#ecfeff;border-top:8px solid #0891b2;padding:18px}"
                    + "header{background:#164e63;color:#fff;padding:28px;margin:-36px -36px 28px}small{color:#cffafe}h1{font-size:34px}h2{color:#0e7490}";
            case "sales-blue" -> base + ".resume{padding:38px}.content{grid-template-columns:1.25fr .75fr}.content aside{display:block;grid-column:2;grid-row:1}.content article{grid-column:1;grid-row:1}"
                    + "header{border-bottom:4px solid #3b82f6}h1{font-size:34px;color:#1d4ed8}h2{color:#1d4ed8;border-bottom:2px solid #3b82f6}";
            case "academic" -> base + ".resume{font-family:'Times New Roman',serif;padding:42px 54px}.content{display:block}header{text-align:center;border-bottom:1px solid #111}"
                    + "h1{font-size:22px}h2{font-size:13px;border-bottom:1px solid #111}p{font-size:12px;line-height:1.34}";
            case "clinical" -> base + ".resume{padding:44px}.content{grid-template-columns:210px 1fr}.content aside{display:block;background:#f0fdf4;border-left:5px solid #16a34a;padding:18px}"
                    + "header{border-bottom:3px solid #166534}h1{font-size:30px;color:#14532d}h2{color:#166534}";
            case "creative-photo" -> base + ".resume{padding:0}.content{grid-template-columns:230px 1fr}.content aside{display:block;background:#f0fdfa;padding:32px;min-height:780px}article{padding:32px}"
                    + "header{background:#155e75;color:#fff;padding:42px 46px;position:relative}header:after{content:'';position:absolute;right:42px;top:28px;width:96px;height:96px;border-radius:50%;background:linear-gradient(135deg,#94a3b8,#111827)}h1{font-size:42px;letter-spacing:.18em}small,header p{color:#d1faf5}h2{letter-spacing:.18em;color:#155e75}";
            case "executive-serif" -> base + ".resume{font-family:Georgia,serif;padding:56px;background:radial-gradient(circle at 84% 7%,rgba(15,23,42,.10),transparent 250px),#fff}.content{display:block}"
                    + "header{text-align:center;border-bottom:2px solid #111827;padding-bottom:18px}h1{font-size:28px;text-transform:uppercase}h2{text-align:center;border-bottom:1px solid #111827;font-size:16px}";
            case "infographic-metrics" -> base + ".resume{padding:38px}.content{grid-template-columns:240px 1fr}.content aside{display:block}.content aside section{background:#f1f5f9;border-left:8px solid #f59e0b;padding:16px}"
                    + "header{background:#111827;color:#fff;margin:-38px -38px 28px;padding:34px 38px}small{color:#cbd5e1}h1{font-size:36px}article section{border-left:4px solid #111827;padding-left:18px}";
            case "product-structured" -> base + ".resume{padding:0 42px 42px}.content{display:block}header{background:#f7d9d7;border-bottom:1px solid #64748b;margin:0 -42px 28px;padding:24px 42px;text-align:center}"
                    + "h1{font-size:28px;font-weight:500}section{display:grid;grid-template-columns:140px 1fr;gap:22px;border-top:1px solid #94a3b8;padding-top:14px}h2{border-top:6px solid #f2d1cf;padding-top:7px}";
            case "timeline-lead" -> base + ".resume{padding:42px}.content{grid-template-columns:220px 1fr}.content aside{display:block;border-right:2px solid #d1d5db;padding-right:24px}"
                    + "article section{border-left:3px solid #64748b;padding-left:18px;position:relative}article section:before{content:'';position:absolute;left:-8px;top:4px;width:12px;height:12px;border-radius:50%;background:#111827}h1{font-size:32px}h2{color:#334155}";
            case "faith-process" -> base + ".resume{padding:32px 42px;font-family:Arial,sans-serif;border-top:8px solid #111}.content{display:block}header{border-bottom:2px solid #111;margin-bottom:12px}"
                    + "h1{font-size:30px;letter-spacing:.04em;text-transform:uppercase}h2{border-bottom:1px solid #111;font-size:12px;letter-spacing:.05em}p{font-size:11px;line-height:1.35}";
            case "academic-cv" -> base + ".resume{font-family:'Times New Roman',serif;padding:34px 48px}.content{display:block}header{text-align:center;border-bottom:1px solid #111}"
                    + "h1{font-size:24px}h2{font-size:12px;border-bottom:1px solid #111}p{font-size:10.8px;line-height:1.25}";
            case "developer-portrait" -> base + ".resume{padding:0;font-family:Inter,Arial,sans-serif}.content{grid-template-columns:230px 1fr}.content aside{display:block;border-right:1px solid #d1d5db;padding:28px}article{padding:28px}"
                    + "header{background:#f3f4f6;padding:36px 34px;position:relative}header:after{content:'';position:absolute;right:44px;top:22px;width:104px;height:104px;border-radius:50%;background:linear-gradient(135deg,#94a3b8,#111827)}h1{font-size:38px;letter-spacing:.22em;font-weight:500}h2{letter-spacing:.22em}";
            case "jonathan-balanced" -> base + ".resume{padding:38px 54px;font-family:Georgia,serif}.content{display:block}header{text-align:center;border-bottom:1px solid #111}"
                    + "h1{font-size:18px}h2{text-align:center;border-bottom:1px solid #111;text-transform:none;font-size:14px}p{font-size:11.5px;line-height:1.38}";
            case "marketing-timeline" -> base + ".resume{padding:44px;font-family:Arial,sans-serif}.content{grid-template-columns:210px 1fr}.content aside{display:block;border-right:2px solid #d1d5db;padding-right:22px}"
                    + "header{border-bottom:2px solid #64748b}h1{font-size:30px;color:#334155}article section{border-left:2px solid #94a3b8;padding-left:20px;position:relative}article section:before{content:'';position:absolute;left:-7px;top:3px;width:12px;height:12px;border-radius:50%;background:#111}h2{color:#334155}";
            case "john-blue" -> base + ".resume{padding:38px;font-family:Arial,sans-serif}.content{grid-template-columns:1.25fr .75fr}.content aside{display:block;grid-column:2;grid-row:1}.content article{grid-column:1;grid-row:1}"
                    + "header{border-bottom:3px solid #2563eb}h1{font-size:34px;color:#2563eb}h2{color:#1d4ed8;border-bottom:2px solid #2563eb}aside section{border-bottom:1px solid #dbeafe}";
            default -> base;
        };
    }

    @Generated
    private static String templateAHtml(String name) {
        return "<!DOCTYPE html><html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + name + " Resume Template</title></head>"
                + "<body class=\"resume-template-a\"><main class=\"resume-page\">"
                + "<header class=\"resume-header\"><div><h1 th:text=\"${fullName}\">Aarav Sharma</h1>"
                + "<p th:text=\"${jobTitle}\">Software Engineer</p></div>"
                + "<ul><li th:text=\"${email}\">email</li><li th:text=\"${phone}\">phone</li><li th:text=\"${location}\">location</li></ul>"
                + "</header>"
                + "<section><h2>Profile</h2><p th:text=\"${summary}\">Summary</p></section>"
                + "<section><h2>Experience</h2><p th:text=\"${experience}\">Experience</p></section>"
                + "<section><h2>Education</h2><p th:text=\"${education}\">Education</p></section>"
                + "<section><h2>Skills</h2><p th:text=\"${skills}\">Skills</p></section>"
                + "</main></body></html>";
    }

    @Generated
    private static String templateACss() {
        return "body{margin:0;background:#eef3f7;color:#172033;font-family:Inter,Arial,sans-serif}"
                + ".resume-page{max-width:820px;min-height:1050px;margin:0 auto;background:#fff;padding:48px;box-sizing:border-box}"
                + ".resume-header{display:flex;justify-content:space-between;gap:32px;border-bottom:3px solid #0b6b5c;padding-bottom:20px;margin-bottom:28px}"
                + "h1{font-size:34px;line-height:1.1;margin:0 0 8px;color:#0c1f33}header p{margin:0;color:#0b6b5c;font-weight:700}"
                + "ul{list-style:none;margin:0;padding:0;text-align:right;color:#54657a;font-size:13px;line-height:1.7}"
                + "section{margin:0 0 22px}h2{font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:#0b6b5c;margin:0 0 8px}"
                + "section p{font-size:15px;line-height:1.6;margin:0;color:#26364a}";
    }

    @Generated
    private static String structuredHtml(String name) {
        return "<!DOCTYPE html><html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + name + "</title></head><body><main class=\"page structured\">"
                + "<header><div class=\"contact\"><span th:text=\"${location}\">Location</span><span th:text=\"${email}\">Email</span></div>"
                + "<h1 th:text=\"${fullName}\">Aarav Sharma</h1><p th:text=\"${jobTitle}\">Software Engineer</p></header>"
                + "<section><h2>Summary</h2><p th:text=\"${summary}\">Summary</p></section>"
                + "<section><h2>Skills</h2><p th:text=\"${skills}\">Skills</p></section>"
                + "<section><h2>Experience</h2><p th:text=\"${experience}\">Experience</p></section>"
                + "<section><h2>Education</h2><p th:text=\"${education}\">Education</p></section>"
                + "</main></body></html>";
    }

    @Generated
    private static String structuredCss() {
        return "body{margin:0;background:#f3f6f9;font-family:Arial,sans-serif;color:#111827}"
                + ".page{max-width:780px;min-height:1040px;margin:0 auto;background:#fff;padding:0 42px 42px;box-sizing:border-box}"
                + "header{background:#f7d9d7;border-bottom:1px solid #68707d;margin:0 -42px 28px;padding:20px 42px;text-align:center}"
                + ".contact{display:flex;justify-content:space-between;font-size:12px;color:#263244;margin-bottom:18px}"
                + "h1{font-size:25px;font-weight:500;letter-spacing:.04em;margin:0 0 8px;text-transform:uppercase}header p{margin:0;font-size:14px}"
                + "section{display:grid;grid-template-columns:130px 1fr;gap:24px;border-top:1px solid #8c95a1;padding:14px 0}"
                + "h2{border-top:5px solid #f2d4d2;font-size:13px;letter-spacing:.02em;margin:0;padding-top:7px;text-transform:uppercase}"
                + "section p{font-size:13px;line-height:1.55;margin:0;white-space:pre-line}";
    }

    @Generated
    private static String sidebarHtml(String name) {
        return "<!DOCTYPE html><html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + name + "</title></head><body><main class=\"page sidebar\">"
                + "<header><h1 th:text=\"${fullName}\">Aarav Sharma</h1><p th:text=\"${jobTitle}\">Software Engineer</p></header>"
                + "<div class=\"columns\"><aside>"
                + "<section><h2>Details</h2><p th:text=\"${email}\">Email</p><p th:text=\"${phone}\">Phone</p><p th:text=\"${location}\">Location</p></section>"
                + "<section><h2>Skills</h2><p th:text=\"${skills}\">Skills</p></section>"
                + "</aside><article>"
                + "<section><h2>Summary</h2><p th:text=\"${summary}\">Summary</p></section>"
                + "<section><h2>Experience</h2><p th:text=\"${experience}\">Experience</p></section>"
                + "<section><h2>Education</h2><p th:text=\"${education}\">Education</p></section>"
                + "</article></div></main></body></html>";
    }

    @Generated
    private static String sidebarCss() {
        return "body{margin:0;background:#f1f5f9;font-family:Inter,Arial,sans-serif;color:#111827}"
                + ".page{max-width:760px;min-height:1040px;margin:0 auto;background:#fff;padding:42px;box-sizing:border-box}"
                + "header{border-bottom:1px solid #c9ced6;padding-bottom:24px;margin-bottom:24px;text-align:left}"
                + "h1{font-size:38px;line-height:.96;letter-spacing:.02em;margin:0 0 12px;text-transform:uppercase;max-width:260px}header p{margin:0;font-size:13px}"
                + ".columns{display:grid;grid-template-columns:170px 1fr;gap:28px}aside{border-right:1px solid #c9ced6;padding-right:24px}"
                + "section{margin-bottom:24px}h2{display:inline-block;border-bottom:2px solid #111827;font-size:18px;letter-spacing:.04em;margin:0 0 14px;text-transform:uppercase}"
                + "p{font-size:13px;line-height:1.48;margin:0 0 10px;white-space:pre-line}article h2{font-size:19px}";
    }

    @Generated
    private static String classicHtml(String name) {
        return "<!DOCTYPE html><html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head>"
                + "<meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + name + "</title></head><body><main class=\"page classic\">"
                + "<header><h1 th:text=\"${fullName}\">Aarav Sharma</h1><p><span th:text=\"${jobTitle}\">Role</span> | <span th:text=\"${email}\">Email</span> | <span th:text=\"${phone}\">Phone</span></p></header>"
                + "<section><h2>Summary</h2><p th:text=\"${summary}\">Summary</p></section>"
                + "<section><h2>Experience</h2><p th:text=\"${experience}\">Experience</p></section>"
                + "<section><h2>Education</h2><p th:text=\"${education}\">Education</p></section>"
                + "<section><h2>Skills</h2><p th:text=\"${skills}\">Skills</p></section>"
                + "</main></body></html>";
    }

    @Generated
    private static String classicCss(boolean minimal) {
        String family = minimal ? "Inter,Arial,sans-serif" : "Georgia,'Times New Roman',serif";
        return "body{margin:0;background:#f8fafc;color:#111827;font-family:" + family + "}"
                + ".page{max-width:760px;min-height:1040px;margin:0 auto;background:#fff;padding:46px 54px;box-sizing:border-box}"
                + "header{border-bottom:2px solid #334155;padding-bottom:12px;margin-bottom:18px}"
                + "h1{font-size:22px;margin:0 0 8px}header p{font-size:12px;margin:0;color:#334155}"
                + "section{margin-bottom:18px}h2{border-bottom:1px solid #334155;font-size:15px;margin:0 0 8px;padding-bottom:4px}"
                + "p{font-size:13px;line-height:1.55;margin:0;white-space:pre-line}";
    }

    @Generated
    private static String compactHtml(String name) {
        return "<!DOCTYPE html><html lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\"><head><meta charset=\"UTF-8\"><title>" + name
                + "</title></head><body><main class=\"page compact\"><header><h1 th:text=\"${fullName}\">Aarav Sharma</h1><p th:text=\"${jobTitle}\">Role</p>"
                + "<small><span th:text=\"${email}\">Email</span> / <span th:text=\"${phone}\">Phone</span> / <span th:text=\"${location}\">Location</span></small></header>"
                + "<section><h2>Profile</h2><p th:text=\"${summary}\">Summary</p></section><section><h2>Experience</h2><p th:text=\"${experience}\">Experience</p></section>"
                + "<section><h2>Skills</h2><p th:text=\"${skills}\">Skills</p></section><section><h2>Education</h2><p th:text=\"${education}\">Education</p></section></main></body></html>";
    }

    @Generated
    private static String compactCss() {
        return "body{margin:0;background:#eef2f7;font-family:Arial,sans-serif;color:#172033}.page{max-width:760px;min-height:980px;margin:0 auto;background:#fff;padding:34px 46px;box-sizing:border-box}"
                + "header{display:grid;grid-template-columns:1fr auto;align-items:end;border-bottom:3px solid #0f766e;padding-bottom:12px;margin-bottom:16px}"
                + "h1{font-size:27px;margin:0;text-transform:uppercase}header p{font-weight:700;margin:4px 0 0;color:#0f766e}small{grid-column:1/-1;color:#64748b;margin-top:8px}"
                + "section{margin-bottom:13px}h2{font-size:12px;letter-spacing:.14em;margin:0 0 6px;color:#0f766e;text-transform:uppercase}p{font-size:12px;line-height:1.42;margin:0;white-space:pre-line}";
    }

    @Generated
    private static String creativeHtml(String name) {
        return sidebarHtml(name).replace("class=\"page sidebar\"", "class=\"page creative\"");
    }

    @Generated
    private static String creativeCss() {
        return "body{margin:0;background:#ecfeff;font-family:Inter,Arial,sans-serif;color:#0f172a}.page{max-width:780px;min-height:1040px;margin:0 auto;background:#fff;box-sizing:border-box;padding:0}"
                + "header{background:#155e75;color:#fff;padding:40px 44px}h1{font-size:40px;line-height:.95;margin:0 0 10px;text-transform:uppercase}header p{margin:0;color:#cffafe}"
                + ".columns{display:grid;grid-template-columns:210px 1fr}aside{background:#f0fdfa;min-height:850px;padding:32px 24px}article{padding:32px}"
                + "h2{font-size:13px;letter-spacing:.16em;margin:0 0 12px;text-transform:uppercase;color:#155e75}p{font-size:13px;line-height:1.5;margin:0 0 14px;white-space:pre-line}";
    }

    @Generated
    private static String executiveHtml(String name) {
        return classicHtml(name).replace("class=\"page classic\"", "class=\"page executive\"");
    }

    @Generated
    private static String executiveCss() {
        return "body{margin:0;background:#e5e7eb;font-family:Georgia,'Times New Roman',serif;color:#111827}.page{max-width:790px;min-height:1040px;margin:0 auto;background:radial-gradient(circle at 85% 5%,rgba(15,23,42,.08),transparent 250px),#fff;padding:52px;box-sizing:border-box}"
                + "header{text-align:center;border-bottom:2px solid #1f2937;padding-bottom:18px;margin-bottom:22px}h1{font-size:26px;margin:0 0 6px;text-transform:uppercase}header p{font-size:12px;margin:0;color:#374151}"
                + "section{margin-bottom:20px}h2{text-align:center;border-bottom:1px solid #1f2937;font-size:16px;margin:0 0 10px;padding-bottom:5px}p{font-size:13px;line-height:1.55;margin:0;white-space:pre-line}";
    }

    @Generated
    private static String infographicHtml(String name) {
        return sidebarHtml(name).replace("class=\"page sidebar\"", "class=\"page infographic\"");
    }

    @Generated
    private static String infographicCss() {
        return "body{margin:0;background:#f8fafc;font-family:Arial,sans-serif;color:#111827}.page{max-width:780px;min-height:1040px;margin:0 auto;background:#fff;padding:38px;box-sizing:border-box}"
                + "header{background:#111827;color:#fff;padding:28px;margin:-38px -38px 28px}h1{font-size:34px;margin:0;text-transform:uppercase}header p{margin:8px 0 0;color:#cbd5e1}"
                + ".columns{display:grid;grid-template-columns:230px 1fr;gap:26px}aside section{background:#f1f5f9;border-left:6px solid #f59e0b;padding:16px}article section{border-left:3px solid #111827;padding-left:18px}"
                + "h2{font-size:13px;letter-spacing:.15em;margin:0 0 10px;text-transform:uppercase}p{font-size:13px;line-height:1.5;margin:0 0 12px;white-space:pre-line}";
    }
}
