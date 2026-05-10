package com.resumeai.auth.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetEmailServiceImpl implements PasswordResetEmailService {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.from:no-reply@resumeai.local}")
    private String fromAddress;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @PostConstruct
    void logMailConfig() {
        boolean hasSender = mailSenderProvider.getIfAvailable() != null;
        boolean hasUsername = mailUsername != null && !mailUsername.isBlank();
        if (mailEnabled && hasSender && hasUsername) {
            log.info("Email configured - from={}, smtp-user={}", fromAddress, mailUsername);
        } else {
            log.warn("Email NOT configured - enabled={}, sender-bean={}, smtp-user={}",
                    mailEnabled, hasSender, hasUsername ? mailUsername : "(blank)");
        }
    }

    @Override
    public void sendPasswordResetOtp(String email, String fullName, String otp, long expiryMinutes) {
        JavaMailSender mailSender = availableMailSender();
        if (mailSender == null) {
            log.info("Password reset OTP requested for {}. OTP: {} (mail sender not configured)", email, otp);
            return;
        }

        try {
            sendHtmlEmail(mailSender, email, "Your ResumeAI Password Reset OTP",
                    buildOtpHtml(fullName, otp, expiryMinutes));
            log.info("Password reset OTP email sent to {}", email);
        } catch (MailException | MessagingException ex) {
            log.warn("Password reset OTP email failed for {}. OTP: {}. Cause: {}",
                    email, otp, ex.getMessage());
        }
    }

    @Override
    public void sendWelcomeEmail(String email, String fullName) {
        JavaMailSender mailSender = availableMailSender();
        if (mailSender == null) {
            log.info("Welcome email requested for {} (mail sender not configured)", email);
            return;
        }

        try {
            sendHtmlEmail(mailSender, email, "Welcome to ResumeAI!", buildWelcomeHtml(fullName));
            log.info("Welcome email sent to {}", email);
        } catch (MailException | MessagingException ex) {
            log.warn("Welcome email failed for {} but account creation continues: {}", email, ex.getMessage());
        }
    }

    @Override
    public void sendAccountDeactivatedEmail(String email, String fullName) {
        JavaMailSender mailSender = availableMailSender();
        if (mailSender == null) {
            log.info("Account deactivation email requested for {} (mail sender not configured)", email);
            return;
        }

        try {
            sendHtmlEmail(mailSender, email, "Your ResumeAI Account Has Been Deactivated",
                    buildDeactivatedHtml(fullName));
            log.info("Account deactivation email sent to {}", email);
        } catch (MailException | MessagingException ex) {
            log.warn("Account deactivation email failed for {}: {}", email, ex.getMessage());
        }
    }

    @Override
    public void sendPlanUpdatedEmail(String email, String fullName, String subscriptionPlan) {
        JavaMailSender mailSender = availableMailSender();
        if (mailSender == null) {
            log.info("Plan update email requested for {} with plan {} (mail sender not configured)", email, subscriptionPlan);
            return;
        }

        try {
            sendHtmlEmail(mailSender, email, "Your ResumeAI Plan Has Been Updated",
                    buildPlanUpdatedHtml(fullName, subscriptionPlan));
            log.info("Plan update email sent to {} for {}", email, subscriptionPlan);
        } catch (MailException | MessagingException ex) {
            log.warn("Plan update email failed for {}: {}", email, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Mail sender resolution
    // -------------------------------------------------------------------------

    private JavaMailSender availableMailSender() {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled) {
            log.debug("Mail sending is disabled (app.mail.enabled=false)");
            return null;
        }
        if (mailSender == null) {
            log.debug("No JavaMailSender bean available — spring-boot-starter-mail may not be configured");
            return null;
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            log.debug("spring.mail.username is blank — SMTP credentials not configured");
            return null;
        }
        return mailSender;
    }

    private void sendHtmlEmail(JavaMailSender mailSender, String to, String subject, String htmlBody)
            throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true); // true = HTML
        mailSender.send(mimeMessage);
    }

    // -------------------------------------------------------------------------
    // HTML email templates
    // -------------------------------------------------------------------------

    private static final String BRAND_COLOR = "#0d9488";
    private static final String BRAND_GRADIENT = "linear-gradient(135deg, #0d9488, #065f53)";

    private String wrapHtml(String title, String bodyContent) {
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
                  <tr><td style="padding:32px 36px 24px;">%s</td></tr>
                  <!-- Footer -->
                  <tr><td style="padding:20px 36px 28px;border-top:1px solid #e5e7eb;text-align:center;">
                    <p style="margin:0;font-size:12px;color:#9ca3af;">This email was sent by ResumeAI. If you did not expect this, you can safely ignore it.</p>
                    <p style="margin:6px 0 0;font-size:12px;color:#9ca3af;">&copy; 2024–2026 ResumeAI. All rights reserved.</p>
                  </td></tr>
                </table>
                </td></tr></table>
                </body></html>
                """.formatted(title, BRAND_GRADIENT, bodyContent);
    }

    private String buildOtpHtml(String fullName, String otp, long expiryMinutes) {
        String name = safeName(fullName);
        String body = """
                <h2 style="margin:0 0 8px;color:#111827;font-size:20px;">Password Reset</h2>
                <p style="margin:0 0 20px;color:#6b7280;font-size:15px;">Hi %s,</p>
                <p style="color:#374151;font-size:15px;line-height:1.6;">
                  We received a request to reset your ResumeAI password. Use the OTP below to set a new password:
                </p>
                <div style="text-align:center;margin:28px 0;">
                  <span style="display:inline-block;background:#f0fdfa;border:2px dashed %s;border-radius:10px;padding:16px 40px;font-size:32px;font-weight:800;letter-spacing:8px;color:%s;">%s</span>
                </div>
                <p style="color:#6b7280;font-size:14px;text-align:center;">This code expires in <strong>%d minutes</strong>.</p>
                <p style="color:#9ca3af;font-size:13px;margin-top:24px;">If you did not request this, you can safely ignore this email — your password will remain unchanged.</p>
                """.formatted(name, BRAND_COLOR, BRAND_COLOR, otp, expiryMinutes);
        return wrapHtml("Password Reset OTP — ResumeAI", body);
    }

    private String buildWelcomeHtml(String fullName) {
        String name = safeName(fullName);
        String body = """
                <h2 style="margin:0 0 8px;color:#111827;font-size:20px;">Welcome to ResumeAI! 🎉</h2>
                <p style="margin:0 0 20px;color:#6b7280;font-size:15px;">Hi %s,</p>
                <p style="color:#374151;font-size:15px;line-height:1.6;">
                  Your account has been created successfully. You're all set to start building standout resumes!
                </p>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
                  <tr>
                    <td style="padding:12px 16px;background:#f0fdfa;border-radius:8px;border-left:4px solid %s;">
                      <p style="margin:0 0 6px;font-weight:700;color:#111827;font-size:14px;">Here's what you can do:</p>
                      <ul style="margin:0;padding-left:18px;color:#374151;font-size:14px;line-height:2;">
                        <li>📝 Build professional resumes with our editor</li>
                        <li>🤖 Generate AI-powered content suggestions</li>
                        <li>✅ Run ATS compatibility checks</li>
                        <li>📄 Export to PDF and DOCX formats</li>
                        <li>💼 Match your resume to job descriptions</li>
                      </ul>
                    </td>
                  </tr>
                </table>
                <div style="text-align:center;margin:28px 0;">
                  <a href="http://localhost:3000/dashboard"
                     style="display:inline-block;background:%s;color:#fff;padding:14px 36px;border-radius:8px;font-size:15px;font-weight:700;text-decoration:none;">
                    Go to Dashboard &rarr;
                  </a>
                </div>
                <p style="color:#6b7280;font-size:14px;">We're excited to help you land your next opportunity!</p>
                <p style="color:#374151;font-size:14px;margin-top:6px;">— The ResumeAI Team</p>
                """.formatted(name, BRAND_COLOR, BRAND_GRADIENT);
        return wrapHtml("Welcome to ResumeAI!", body);
    }

    private String buildDeactivatedHtml(String fullName) {
        String name = safeName(fullName);
        String body = """
                <h2 style="margin:0 0 8px;color:#111827;font-size:20px;">Account Deactivated</h2>
                <p style="margin:0 0 20px;color:#6b7280;font-size:15px;">Hi %s,</p>
                <p style="color:#374151;font-size:15px;line-height:1.6;">
                  Your ResumeAI account has been deactivated. You will not be able to access your workspace until an administrator reactivates your account.
                </p>
                <div style="background:#fef2f2;border-left:4px solid #ef4444;border-radius:8px;padding:14px 18px;margin:24px 0;">
                  <p style="margin:0;color:#991b1b;font-size:14px;font-weight:600;">
                    ⚠️ If you believe this was a mistake, please contact the ResumeAI admin team.
                  </p>
                </div>
                <p style="color:#374151;font-size:14px;margin-top:20px;">— The ResumeAI Team</p>
                """.formatted(name);
        return wrapHtml("Account Deactivated — ResumeAI", body);
    }

    private String buildPlanUpdatedHtml(String fullName, String subscriptionPlan) {
        String name = safeName(fullName);
        String plan = subscriptionPlan == null || subscriptionPlan.isBlank() ? "updated" : subscriptionPlan.toUpperCase();
        boolean isPremium = "PREMIUM".equalsIgnoreCase(plan);
        String planBadgeBg = isPremium ? "linear-gradient(135deg, #f59e0b, #d97706)" : BRAND_GRADIENT;
        String planEmoji = isPremium ? "👑" : "📋";

        String body = """
                <h2 style="margin:0 0 8px;color:#111827;font-size:20px;">Plan Updated</h2>
                <p style="margin:0 0 20px;color:#6b7280;font-size:15px;">Hi %s,</p>
                <p style="color:#374151;font-size:15px;line-height:1.6;">
                  Your ResumeAI subscription plan has been updated.
                </p>
                <div style="text-align:center;margin:28px 0;">
                  <span style="display:inline-block;background:%s;color:#fff;padding:14px 36px;border-radius:10px;font-size:18px;font-weight:800;letter-spacing:1px;">
                    %s %s Plan
                  </span>
                </div>
                <p style="color:#374151;font-size:15px;line-height:1.6;text-align:center;">
                  Sign in to ResumeAI to explore the features available on your current plan.
                </p>
                <div style="text-align:center;margin:24px 0;">
                  <a href="http://localhost:3000/dashboard"
                     style="display:inline-block;background:%s;color:#fff;padding:12px 32px;border-radius:8px;font-size:14px;font-weight:700;text-decoration:none;">
                    View My Plan &rarr;
                  </a>
                </div>
                <p style="color:#374151;font-size:14px;margin-top:20px;">— The ResumeAI Team</p>
                """.formatted(name, planBadgeBg, planEmoji, plan, BRAND_GRADIENT);
        return wrapHtml("Plan Updated — ResumeAI", body);
    }

    private static String safeName(String fullName) {
        return fullName == null || fullName.isBlank() ? "there" : fullName;
    }
}
