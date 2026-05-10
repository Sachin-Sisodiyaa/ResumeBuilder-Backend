package com.resumeai.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationEmailServiceImplTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Test
    void blankRecipientAndMissingMailSenderDoNotThrow() {
        NotificationEmailServiceImpl service = new NotificationEmailServiceImpl(mailSenderProvider);
        ReflectionTestUtils.setField(service, "mailEnabled", true);

        assertDoesNotThrow(() -> service.sendNotificationEmail(null, "Title", "Message", "/go"));
        assertDoesNotThrow(() -> service.sendNotificationEmail("   ", "Title", "Message", "/go"));

        // No mail sender available — should still not throw
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);
        assertDoesNotThrow(() -> service.sendNotificationEmail("user@example.com", "Title", "Message", "/go"));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void configuredMailSenderReceivesComposedMessage() {
        NotificationEmailServiceImpl service = new NotificationEmailServiceImpl(mailSenderProvider);
        ReflectionTestUtils.setField(service, "fromAddress", "from@example.com");
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "mailUsername", "smtp-user@example.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendNotificationEmail("user@example.com", "Title", " Message ", "/go");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
