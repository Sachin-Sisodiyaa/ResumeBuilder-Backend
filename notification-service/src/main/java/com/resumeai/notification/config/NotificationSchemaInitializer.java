package com.resumeai.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class NotificationSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Bean
    ApplicationRunner notificationReadColumnCompatibility() {
        return args -> ensureReadColumn();
    }

    private void ensureReadColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE notifications ADD COLUMN `read` TINYINT(1) NOT NULL DEFAULT 0");
            log.info("Added missing notifications.`read` column");
        } catch (RuntimeException ex) {
            log.debug("notifications.`read` column already exists or table is not ready yet: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE notifications ADD COLUMN read_status TINYINT(1) NOT NULL DEFAULT 0");
            log.info("Added missing notifications.read_status column");
        } catch (RuntimeException ex) {
            log.debug("notifications.read_status column already exists or table is not ready yet: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE notifications MODIFY COLUMN `read` TINYINT(1) NOT NULL DEFAULT 0");
            log.info("Ensured notifications.`read` has a default value");
        } catch (RuntimeException ex) {
            log.debug("Could not modify notifications.`read` default: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("ALTER TABLE notifications MODIFY COLUMN read_status TINYINT(1) NOT NULL DEFAULT 0");
            log.info("Ensured notifications.read_status has a default value");
        } catch (RuntimeException ex) {
            log.debug("Could not modify notifications.read_status default: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("UPDATE notifications SET `read` = read_status WHERE read_status IS NOT NULL");
            log.info("Copied notification read_status values into `read` column");
        } catch (RuntimeException ex) {
            log.debug("No notification read_status compatibility copy needed: {}", ex.getMessage());
        }

        try {
            jdbcTemplate.execute("UPDATE notifications SET read_status = `read` WHERE `read` IS NOT NULL");
            log.info("Copied notification `read` values into read_status column");
        } catch (RuntimeException ex) {
            log.debug("No notification `read` compatibility copy needed: {}", ex.getMessage());
        }
    }
}
