package com.resumeai.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.resumeai.auth.model.AuditLog;
import com.resumeai.auth.repository.AuditLogRepository;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class AuthSupportServicesTest {

    @Test
    void auditLogServiceRecordsAndQueries() {
        AuditLogRepository repository = org.mockito.Mockito.mock(AuditLogRepository.class);
        AuditLog saved = new AuditLog();
        saved.setAction("LOGIN");
        when(repository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByOrderByTimestampDesc()).thenReturn(List.of(saved));
        when(repository.findByEntityTypeAndEntityIdOrderByTimestampDesc("USER", "1")).thenReturn(List.of(saved));
        when(repository.findByActorIdOrderByTimestampDesc(1L)).thenReturn(List.of(saved));
        when(repository.findByActionIgnoreCaseOrderByTimestampDesc("LOGIN")).thenReturn(List.of(saved));
        AuditLogService service = new AuditLogService(repository);

        AuditLog log = service.recordAudit(1L, "admin@example.com", "LOGIN", "USER", "1", "before", "after");
        assertEquals("LOGIN", log.getAction());
        assertEquals(1, service.getAll().size());
        assertEquals(1, service.getByEntity("USER", "1").size());
        assertEquals(1, service.getByActor(1L).size());
        assertEquals(1, service.getByAction("LOGIN").size());
        service.recordAudit(1L, "admin@example.com", "LOGOUT", "USER", "1");
        verify(repository, org.mockito.Mockito.times(2)).save(any(AuditLog.class));
    }

    @Test
    void passwordResetEmailFallsBackWhenSenderMissing() {
        PasswordResetEmailServiceImpl service = new PasswordResetEmailServiceImpl(provider(null));

        assertDoesNotThrow(() -> service.sendPasswordResetOtp("user@example.com", "", "123456", 30));
        assertDoesNotThrow(() -> service.sendWelcomeEmail("user@example.com", null));
        assertDoesNotThrow(() -> service.sendAccountDeactivatedEmail("user@example.com", "Alice"));
        assertDoesNotThrow(() -> service.sendPlanUpdatedEmail("user@example.com", "Alice", "premium"));
    }

    @Test
    void passwordResetEmailSendsMessagesWhenSenderConfigured() {
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        MimeMessage mimeMessage = org.mockito.Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        PasswordResetEmailServiceImpl service = new PasswordResetEmailServiceImpl(provider(mailSender));
        ReflectionTestUtils.setField(service, "fromAddress", "from@example.com");
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailUsername", "test@example.com");

        service.sendPasswordResetOtp("user@example.com", "Alice", "654321", 30);
        service.sendWelcomeEmail("user@example.com", "Alice");
        service.sendAccountDeactivatedEmail("user@example.com", "Alice");
        service.sendPlanUpdatedEmail("user@example.com", "Alice", "premium");
        service.sendPlanUpdatedEmail("user@example.com", "", "");

        verify(mailSender, times(5)).send(any(MimeMessage.class));
    }

    @Test
    void passwordResetEmailHandlesDisabledBlankUsernameAndSendFailures() {
        JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
        MimeMessage mimeMessage = org.mockito.Mockito.mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        PasswordResetEmailServiceImpl disabled = new PasswordResetEmailServiceImpl(provider(mailSender));
        ReflectionTestUtils.setField(disabled, "mailEnabled", false);
        ReflectionTestUtils.setField(disabled, "mailUsername", "test@example.com");

        disabled.logMailConfig();
        disabled.sendPasswordResetOtp("user@example.com", "Alice", "111111", 5);
        verify(mailSender, times(0)).send(any(MimeMessage.class));

        PasswordResetEmailServiceImpl blankUsername = new PasswordResetEmailServiceImpl(provider(mailSender));
        ReflectionTestUtils.setField(blankUsername, "mailEnabled", true);
        ReflectionTestUtils.setField(blankUsername, "mailUsername", "");
        blankUsername.logMailConfig();
        blankUsername.sendWelcomeEmail("user@example.com", "Alice");
        verify(mailSender, times(0)).send(any(MimeMessage.class));

        JavaMailSender failingSender = org.mockito.Mockito.mock(JavaMailSender.class);
        MimeMessage failingMessage = org.mockito.Mockito.mock(MimeMessage.class);
        when(failingSender.createMimeMessage()).thenReturn(failingMessage);
        org.mockito.Mockito.doThrow(new MailSendException("smtp down"))
                .when(failingSender).send(any(MimeMessage.class));
        PasswordResetEmailServiceImpl failing = new PasswordResetEmailServiceImpl(provider(failingSender));
        ReflectionTestUtils.setField(failing, "fromAddress", "from@example.com");
        ReflectionTestUtils.setField(failing, "mailEnabled", true);
        ReflectionTestUtils.setField(failing, "mailUsername", "test@example.com");

        failing.logMailConfig();
        assertDoesNotThrow(() -> failing.sendPasswordResetOtp("user@example.com", "Alice", "222222", 5));
        assertDoesNotThrow(() -> failing.sendWelcomeEmail("user@example.com", "Alice"));
        assertDoesNotThrow(() -> failing.sendAccountDeactivatedEmail("user@example.com", "Alice"));
        assertDoesNotThrow(() -> failing.sendPlanUpdatedEmail("user@example.com", "Alice", "FREE"));

        verify(failingSender, times(4)).send(any(MimeMessage.class));
    }

    private ObjectProvider<JavaMailSender> provider(JavaMailSender mailSender) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject(Object... args) {
                return mailSender;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return mailSender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return mailSender;
            }

            @Override
            public JavaMailSender getObject() {
                return mailSender;
            }
        };
    }
}
