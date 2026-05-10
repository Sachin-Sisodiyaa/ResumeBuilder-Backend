package com.resumeai.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email dispatch implementation using Spring's JavaMailSender.
 *
 * <p>When no mail sender bean is available (e.g. dev without SMTP config), the
 * notification is logged to stdout instead of throwing an exception — so the
 * rest of the request is never blocked by email unavailability.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationEmailServiceImpl implements NotificationEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:no-reply@resumeai.local}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Override
    @Async
    public void sendNotificationEmail(String recipientEmail, String title,
                                      String message, String actionUrl) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }

        if (!mailEnabled) {
            log.info("[EMAIL-STUB] Mail disabled. To: {} | Subject: {}", recipientEmail, title);
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.info("[EMAIL-STUB] No JavaMailSender bean available. To: {} | Subject: {}", recipientEmail, title);
            return;
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            log.info("[EMAIL-STUB] SMTP username not configured. To: {} | Subject: {}", recipientEmail, title);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject("[ResumeAI] " + title);
            helper.setText(buildHtmlBody(title, message, actionUrl), true);
            mailSender.send(mimeMessage);
            log.info("Notification email sent to {} | Subject: {}", recipientEmail, title);
        } catch (MailException | MessagingException ex) {
            log.warn("Notification email failed for {} but in-app notification was kept: {}",
                    recipientEmail, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    private static final String BRAND_GRADIENT = "linear-gradient(135deg, #0d9488, #065f53)";

    private String buildHtmlBody(String title, String message, String actionUrl) {
        String safeTitle = title == null ? "Notification" : title;
        String safeMessage = message == null ? "" : message.trim().replace("\n", "<br>");
        String actionButton = "";
        if (actionUrl != null && !actionUrl.isBlank()) {
            String fullUrl = actionUrl.startsWith("http") ? actionUrl
                    : "http://localhost:3000" + actionUrl;
            actionButton = """
                    <div style="text-align:center;margin:24px 0;">
                      <a href="%s"
                         style="display:inline-block;background:%s;color:#fff;padding:12px 32px;border-radius:8px;font-size:14px;font-weight:700;text-decoration:none;">
                        View Details &rarr;
                      </a>
                    </div>
                    """.formatted(fullUrl, BRAND_GRADIENT);
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:32px 16px;">
                <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                       style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                  <!-- Header -->
                  <tr><td style="background:%s;padding:28px 32px;text-align:center;">
                    <span style="display:inline-block;width:42px;height:42px;line-height:42px;border-radius:10px;background:rgba(255,255,255,0.2);color:#fff;font-weight:800;font-size:18px;margin-right:10px;vertical-align:middle;">RA</span>
                    <span style="color:#ffffff;font-size:22px;font-weight:700;vertical-align:middle;letter-spacing:0.5px;">ResumeAI</span>
                  </td></tr>
                  <!-- Body -->
                  <tr><td style="padding:32px 36px 24px;">
                    <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">%s</h2>
                    <p style="color:#374151;font-size:15px;line-height:1.7;">%s</p>
                    %s
                  </td></tr>
                  <!-- Footer -->
                  <tr><td style="padding:20px 36px 28px;border-top:1px solid #e5e7eb;text-align:center;">
                    <p style="margin:0;font-size:12px;color:#9ca3af;">This email was sent by ResumeAI.</p>
                    <p style="margin:6px 0 0;font-size:12px;color:#9ca3af;">&copy; 2024–2026 ResumeAI. All rights reserved.</p>
                  </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """.formatted(safeTitle, BRAND_GRADIENT, safeTitle, safeMessage, actionButton);
    }
}
