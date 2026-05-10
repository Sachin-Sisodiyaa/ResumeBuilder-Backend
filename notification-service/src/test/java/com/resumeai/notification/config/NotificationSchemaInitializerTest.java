package com.resumeai.notification.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationSchemaInitializerTest {

    @Test
    void runnerAttemptsAllCompatibilityStatementsEvenWhenSomeFail() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        doThrow(new RuntimeException("already exists"))
                .doNothing()
                .doThrow(new RuntimeException("modify failed"))
                .doNothing()
                .doNothing()
                .doThrow(new RuntimeException("copy failed"))
                .when(jdbcTemplate).execute(anyString());

        NotificationSchemaInitializer initializer = new NotificationSchemaInitializer(jdbcTemplate);
        ApplicationRunner runner = initializer.notificationReadColumnCompatibility();

        runner.run(null);

        verify(jdbcTemplate, times(6)).execute(anyString());
    }
}
